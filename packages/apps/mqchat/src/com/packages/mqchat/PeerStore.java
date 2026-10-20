package com.packages.mqchat;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import com.packages.mqchat.PeerProtocol.*;

/** SQLite owns identity, pinned friend keys, requests, history and a durable send outbox. */
public final class PeerStore implements AutoCloseable {
    public static final class Contact {
        public final Profile profile;public final String state,request;
        Contact(Profile profile,String state,String request){this.profile=profile;this.state=state;this.request=request;}
    }
    public static final class Message {
        public final String id,peer,body,status;public final boolean own;public final long time;
        Message(String id,String peer,String body,boolean own,long time,String status){this.id=id;this.peer=peer;this.body=body;this.own=own;this.time=time;this.status=status;}
    }
    public static final class Pending {
        public final String id,topic;public final byte[] bytes;public final boolean retain;
        Pending(String id,String topic,byte[] bytes,boolean retain){this.id=id;this.topic=topic;this.bytes=bytes;this.retain=retain;}
    }
    interface Work {void run()throws Exception;}
    private final Connection db;public final Path path;
    public PeerStore(Path file)throws Exception{
        path=file.toAbsolutePath().normalize();Files.createDirectories(path.getParent());db=open(path,false);privateFile(path);
        execute("PRAGMA busy_timeout=5000");execute("PRAGMA foreign_keys=ON");execute("PRAGMA journal_mode=WAL");execute("PRAGMA synchronous=FULL");
        execute("CREATE TABLE IF NOT EXISTS identity(slot INTEGER PRIMARY KEY CHECK(slot=1),uuid TEXT NOT NULL,name TEXT NOT NULL,avatar BLOB NOT NULL,pub BLOB NOT NULL,secret BLOB NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS contacts(uuid TEXT PRIMARY KEY,name TEXT NOT NULL,avatar BLOB NOT NULL,pub BLOB NOT NULL,state TEXT NOT NULL,request TEXT NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS messages(id TEXT PRIMARY KEY,peer TEXT NOT NULL REFERENCES contacts(uuid) ON DELETE CASCADE,body TEXT NOT NULL,own INTEGER NOT NULL,time INTEGER NOT NULL,status TEXT NOT NULL)");
        execute("CREATE INDEX IF NOT EXISTS messages_peer_time ON messages(peer,time)");
        execute("CREATE TABLE IF NOT EXISTS outbox(id TEXT PRIMARY KEY,topic TEXT NOT NULL,payload BLOB NOT NULL,retain INTEGER NOT NULL)");execute("PRAGMA user_version=2");
        if(identity()==null)saveIdentity(Identity.create());
    }
    private static void privateFile(Path file)throws IOException{
        try{Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored){}
    }
    private static Connection open(Path file,boolean readonly)throws Exception{
        Driver driver=(Driver)Class.forName("org.sqlite.JDBC",true,PeerStore.class.getClassLoader()).newInstance();Properties options=new Properties();if(readonly)options.setProperty("open_mode","1");return driver.connect("jdbc:sqlite:"+file.toString(),options);
    }
    private void execute(String sql)throws SQLException{try(Statement statement=db.createStatement()){statement.execute(sql);}}
    private void update(String sql,Object...values)throws SQLException{try(PreparedStatement statement=db.prepareStatement(sql)){for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);statement.executeUpdate();}}
    public synchronized void transaction(Work work)throws Exception{db.setAutoCommit(false);try{work.run();db.commit();}catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(true);}}
    public synchronized Identity identity()throws SQLException{try(Statement statement=db.createStatement();ResultSet row=statement.executeQuery("SELECT * FROM identity WHERE slot=1")){return row.next()?new Identity(row.getString("uuid"),row.getString("name"),row.getBytes("avatar"),row.getBytes("pub"),row.getBytes("secret")):null;}}
    public synchronized void saveIdentity(Identity value)throws SQLException{update("INSERT OR REPLACE INTO identity VALUES(1,?,?,?,?,?)",value.uuid,value.name,value.avatar,value.key,value.secret);}
    public synchronized List<Contact> contacts()throws SQLException{List<Contact> result=new ArrayList<>();try(Statement statement=db.createStatement();ResultSet rows=statement.executeQuery("SELECT * FROM contacts ORDER BY CASE state WHEN 'accepted' THEN 0 WHEN 'incoming' THEN 1 ELSE 2 END,name,uuid")){while(rows.next())result.add(contact(rows));}return result;}
    private static Contact contact(ResultSet row)throws SQLException{return new Contact(new Profile(row.getString("uuid"),row.getString("name"),row.getBytes("avatar"),row.getBytes("pub")),row.getString("state"),row.getString("request"));}
    public synchronized Contact contact(String uuid)throws SQLException{try(PreparedStatement statement=db.prepareStatement("SELECT * FROM contacts WHERE uuid=?")){statement.setString(1,uuid);try(ResultSet rows=statement.executeQuery()){return rows.next()?contact(rows):null;}}}
    public synchronized void saveContact(Profile profile,String state,String request)throws Exception{
        if(!Arrays.asList("accepted","outgoing","incoming","declined").contains(state))throw new IllegalArgumentException("好友状态无效");PeerProtocol.id(request);Contact old=contact(profile.uuid);if(old!=null&&!Arrays.equals(old.profile.key,profile.key))throw new SecurityException("对方身份密钥变化，已阻止替换");
        update("INSERT INTO contacts VALUES(?,?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,avatar=excluded.avatar,pub=excluded.pub,state=excluded.state,request=excluded.request",profile.uuid,profile.name,profile.avatar,profile.key,state,request);
    }
    public synchronized void enqueue(String id,String topic,byte[] payload,boolean retained)throws SQLException{update("INSERT OR IGNORE INTO outbox VALUES(?,?,?,?)",id,topic,payload,retained?1:0);}
    public synchronized List<Pending> pending()throws SQLException{return pending("");}
    public synchronized List<Pending> pending(String after)throws SQLException{
        List<Pending> result=new ArrayList<>();try(PreparedStatement statement=db.prepareStatement("SELECT * FROM outbox WHERE id>? ORDER BY id LIMIT 128")){
            statement.setString(1,after);try(ResultSet rows=statement.executeQuery()){while(rows.next())result.add(new Pending(rows.getString("id"),rows.getString("topic"),rows.getBytes("payload"),rows.getInt("retain")!=0));}
        }return result;
    }
    public synchronized void acknowledged(String id)throws SQLException{transactionSql(()->{update("DELETE FROM outbox WHERE id=? AND id NOT IN (SELECT id FROM messages WHERE own=1 AND status<>'已送达')",id);update("UPDATE messages SET status='已发送' WHERE id=? AND status='待发送'",id);});}
    private void transactionSql(Work work)throws SQLException{try{transaction(work);}catch(SQLException e){throw e;}catch(Exception e){throw new SQLException(e);}}
    public synchronized boolean addMessage(String id,String peer,String body,boolean own,long time,String status)throws SQLException{try(PreparedStatement statement=db.prepareStatement("INSERT OR IGNORE INTO messages VALUES(?,?,?,?,?,?)")){statement.setString(1,id);statement.setString(2,peer);statement.setString(3,body);statement.setInt(4,own?1:0);statement.setLong(5,time);statement.setString(6,status);return statement.executeUpdate()!=0;}}
    public synchronized void delivered(String id,String peer)throws SQLException{transactionSql(()->{update("UPDATE messages SET status='已送达' WHERE id=? AND peer=? AND own=1",id,peer);update("DELETE FROM outbox WHERE id IN (SELECT id FROM messages WHERE id=? AND peer=? AND own=1 AND status='已送达')",id,peer);});}
    public synchronized List<Message> messages(String peer)throws SQLException{List<Message> result=new ArrayList<>();try(PreparedStatement statement=db.prepareStatement("SELECT * FROM (SELECT * FROM messages WHERE peer=? ORDER BY time DESC,id DESC LIMIT 300) ORDER BY time,id")){statement.setString(1,peer);try(ResultSet rows=statement.executeQuery()){while(rows.next())result.add(new Message(rows.getString("id"),rows.getString("peer"),rows.getString("body"),rows.getInt("own")!=0,rows.getLong("time"),rows.getString("status")));}}return result;}
    public synchronized void exportBackup(Path destination)throws Exception{
        Path target=destination.toAbsolutePath().normalize();if(target.equals(path))throw new IllegalArgumentException("备份路径不能是当前数据库");Files.createDirectories(target.getParent());Path temp=Files.createTempFile(target.getParent(),"mqchat-backup-",".sqlite");Files.delete(temp);
        try{try(PreparedStatement statement=db.prepareStatement("VACUUM INTO ?")){statement.setString(1,temp.toString());statement.execute();}privateFile(temp);try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
    }
    public synchronized void importBackup(Path source)throws Exception{
        Path file=source.toAbsolutePath().normalize();if(file.equals(path)||!Files.isRegularFile(file)||Files.size(file)>128*1024*1024L)throw new IllegalArgumentException("请选择有效的 MQChat SQLite 备份（最大 128 MB）");
        Identity identity;List<Contact> contacts=new ArrayList<>();List<Message> messages=new ArrayList<>();List<Pending> outbox=new ArrayList<>();
        try(Connection input=open(file,true);Statement statement=input.createStatement()){
            statement.execute("PRAGMA trusted_schema=OFF");statement.execute("PRAGMA query_only=ON");
            try(ResultSet rows=statement.executeQuery("PRAGMA user_version")){if(!rows.next()||rows.getInt(1)!=2)throw new IOException("备份版本不支持");}
            try(ResultSet rows=statement.executeQuery("PRAGMA quick_check")){if(!rows.next()||!"ok".equals(rows.getString(1)))throw new IOException("备份数据库损坏");}
            try(ResultSet rows=statement.executeQuery("SELECT * FROM identity WHERE slot=1")){if(!rows.next())throw new IOException("备份缺少身份");identity=new Identity(rows.getString("uuid"),rows.getString("name"),rows.getBytes("avatar"),rows.getBytes("pub"),rows.getBytes("secret"));}
            Set<String> ids=new HashSet<>();try(ResultSet rows=statement.executeQuery("SELECT * FROM contacts LIMIT 5001")){while(rows.next()){Contact contact=contact(rows);if(contact.profile.uuid.equals(identity.uuid)||!ids.add(contact.profile.uuid)||!Arrays.asList("accepted","outgoing","incoming","declined").contains(contact.state))throw new IOException("好友记录无效");PeerProtocol.id(contact.request);contacts.add(contact);}if(contacts.size()>5000)throw new IOException("好友数量过多");}
            try(ResultSet rows=statement.executeQuery("SELECT * FROM messages ORDER BY time DESC LIMIT 20001")){while(rows.next()){String id=PeerProtocol.id(rows.getString("id")),peer=PeerProtocol.id(rows.getString("peer")),body=rows.getString("body");if(!ids.contains(peer)||body.codePointCount(0,body.length())>2000)throw new IOException("聊天记录无效");messages.add(new Message(id,peer,body,rows.getInt("own")!=0,rows.getLong("time"),rows.getString("status")));}if(messages.size()>20000)throw new IOException("备份消息数量过多");}
        }
        try(Connection input=open(file,true);Statement statement=input.createStatement()){
            statement.execute("PRAGMA trusted_schema=OFF");statement.execute("PRAGMA query_only=ON");
            try(ResultSet rows=statement.executeQuery("SELECT * FROM outbox LIMIT 5001")){
                Set<String> ids=new HashSet<>();for(Contact contact:contacts)ids.add(contact.profile.uuid);
                while(rows.next()){
                    String id=PeerProtocol.id(rows.getString("id")),topic=rows.getString("topic");byte[] bytes=rows.getBytes("payload");boolean retain=rows.getInt("retain")!=0;
                    Envelope envelope=PeerProtocol.decode(bytes);boolean control=Arrays.asList("REQUEST","ACCEPT","REJECT").contains(envelope.type);
                    if(!envelope.id.equals(id)||!envelope.from.uuid.equals(identity.uuid)||!Arrays.equals(envelope.from.key,identity.key)||!ids.contains(envelope.to))throw new IOException("备份待发送记录身份不匹配");
                    if(control?(!retain||!topic.equals(PeerProtocol.inbox(envelope.to,identity.uuid))):(retain||!Arrays.asList("CHAT","DELIVERED").contains(envelope.type)||!topic.equals(PeerProtocol.room(identity.uuid,envelope.to))))throw new IOException("备份待发送记录 Topic 无效");
                    outbox.add(new Pending(id,topic,bytes,retain));
                }
                if(outbox.size()>5000)throw new IOException("备份待发送记录过多");
            }
        }
        final Identity imported=identity;transaction(()->{execute("DELETE FROM outbox");execute("DELETE FROM messages");execute("DELETE FROM contacts");saveIdentity(imported);for(Contact contact:contacts)saveContact(contact.profile,contact.state,contact.request);for(Message message:messages)addMessage(message.id,message.peer,message.body,message.own,message.time,message.status);for(Pending pending:outbox)enqueue(pending.id,pending.topic,pending.bytes,pending.retain);});
    }
    @Override public synchronized void close()throws SQLException{db.close();}
}
