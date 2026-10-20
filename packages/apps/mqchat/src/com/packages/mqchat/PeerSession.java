package com.packages.mqchat;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.packages.mqchat.PeerProtocol.*;
import com.packages.mqchat.PeerStore.*;

/** Friendship state machine. Network callbacks, user actions and SQLite writes are serialized. */
public final class PeerSession implements AutoCloseable {
    public interface Listener {void changed();}
    public final PeerStore store;
    private Identity self;private PeerMqtt client;private final Listener listener;
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"mqchat-peer-heartbeat");t.setDaemon(true);return t;});
    private final Map<String,Presence> presence=new HashMap<>();private final Map<String,Lookup> lookups=new HashMap<>();private final Set<String> sending=new HashSet<>();
    private String session,outboxCursor="";private boolean closed;private volatile String status="离线 · 连接后接收好友申请";
    private static final class Presence {String session;long timestamp;boolean online;Presence(String session,long timestamp,boolean online){this.session=session;this.timestamp=timestamp;this.online=online;}}
    private static final class Lookup {String uuid;long expires;Lookup(String uuid){this.uuid=uuid;expires=System.currentTimeMillis()+10000;}}
    public PeerSession(Path database,Listener listener)throws Exception{store=new PeerStore(database);self=store.identity();this.listener=listener;timer.scheduleAtFixedRate(this::tick,2,10,TimeUnit.SECONDS);}
    public synchronized Identity identity(){return self;}
    public String status(){return status;}
    public synchronized boolean connected(){return client!=null&&client.ready();}
    public synchronized boolean online(String uuid){Presence value=presence.get(uuid);return connected()&&value!=null&&value.online&&System.currentTimeMillis()-value.timestamp<45000;}
    private void changed(){listener.changed();}
    private void error(Exception e){status="操作失败："+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());changed();}
    public synchronized void connect(){if(client!=null||closed)return;try{
        session=UUID.randomUUID().toString();String host=System.getProperty("framework.mqchat.host","broker.emqx.io");boolean tls=Boolean.parseBoolean(System.getProperty("framework.mqchat.tls","true"));int port=Integer.parseInt(System.getProperty("framework.mqchat.port",tls?"8883":"1883"));
        final PeerMqtt[] reference=new PeerMqtt[1];
        PeerMqtt transport=new PeerMqtt(host,port,tls,"mqp"+self.uuid.replace("-",""),PeerProtocol.status(self.uuid),packet("PRESENCE",UUID.randomUUID().toString(),"",session,"offline"),new PeerMqtt.Listener(){
            public void state(boolean ready,String detail){synchronized(PeerSession.this){if(closed||client!=reference[0])return;status=detail;sending.clear();if(ready)try{announce();for(Contact contact:store.contacts()){if(contact.state.equals("outgoing"))queueRequest(contact.profile,contact.request);else if(contact.state.equals("accepted"))queueResponse(contact,"ACCEPT");}flush();}catch(Exception e){error(e);}changed();}}
            public void message(String topic,byte[] body,boolean retained){synchronized(PeerSession.this){if(closed||client!=reference[0])return;try{receive(topic,body,retained);}catch(Exception ignored){/* Invalid, forged and unrecognized packets never change persisted state. */}}}
            public void acknowledged(String id){synchronized(PeerSession.this){if(closed||client!=reference[0])return;try{store.acknowledged(id);flush();changed();}catch(Exception e){error(e);}}}
        });reference[0]=transport;client=transport;
        transport.subscribe(PeerProtocol.inbox(self.uuid));for(Contact contact:store.contacts())subscribe(contact);transport.start();status="正在连接 EMQX…";changed();
    }catch(Exception e){if(client!=null)client.close();client=null;error(e);}}
    public synchronized void disconnect(){PeerMqtt old=client;client=null;if(old!=null)old.close();sending.clear();presence.clear();lookups.clear();status="已离线";changed();}
    private void subscribe(Contact contact){if(client==null||contact.state.equals("declined"))return;client.subscribe(PeerProtocol.status(contact.profile.uuid));if(contact.state.equals("accepted"))client.subscribe(PeerProtocol.room(self.uuid,contact.profile.uuid));}
    private byte[] packet(String type,String id,String to,String ref,String body)throws Exception{return PeerProtocol.encode(self,type,id,to,ref,body);}
    private void announce()throws Exception{if(!connected())return;client.publish("directory",PeerProtocol.directory(self.code()),packet("PROFILE",UUID.randomUUID().toString(),"","",""),true);client.publish("presence",PeerProtocol.status(self.uuid),packet("PRESENCE",UUID.randomUUID().toString(),"",session,"online"),true);}
    private synchronized void tick(){if(closed)return;try{for(Iterator<Map.Entry<String,Lookup>> it=lookups.entrySet().iterator();it.hasNext();){Map.Entry<String,Lookup> entry=it.next();if(System.currentTimeMillis()>entry.getValue().expires){if(client!=null)client.unsubscribe(PeerProtocol.directory(entry.getKey()));it.remove();status="未找到该用户，请确认展示码并让对方至少连接一次";}}
        if(connected()){announce();sending.clear();flush();}changed();}catch(Exception e){error(e);}}
    public synchronized void updateProfile(String name,byte[] avatar){try{Identity updated=new Identity(self.uuid,name,avatar,self.key,self.secret);store.saveIdentity(updated);self=updated;announce();status="个人资料已保存";changed();}catch(Exception e){error(e);}}
    public synchronized void addFriend(String input){try{
        if(!connected())throw new IllegalStateException("请先连接网络");String value=input.trim().toUpperCase(Locale.ROOT).replace(" ","").replace("-","");String uuid="",code;
        if(value.length()==32){uuid=PeerProtocol.id(input.trim().toLowerCase(Locale.ROOT));code=PeerProtocol.shortCode(uuid);}else{code=value;PeerProtocol.directory(code);}
        if(code.equals(self.code()))throw new IllegalArgumentException("不能添加自己");if(lookups.size()>=8)throw new IllegalStateException("请等待当前查找完成");lookups.put(code,new Lookup(uuid));client.subscribe(PeerProtocol.directory(code));status="正在查找 "+code+"…";changed();
    }catch(Exception e){error(e);}}
    private void queueRequest(Profile peer,String request)throws Exception{store.enqueue(request,PeerProtocol.inbox(peer.uuid,self.uuid),packet("REQUEST",request,peer.uuid,request,""),true);}
    private void queueResponse(Contact contact,String type)throws Exception{String id=UUID.randomUUID().toString();store.enqueue(id,PeerProtocol.inbox(contact.profile.uuid,self.uuid),packet(type,id,contact.profile.uuid,contact.request,""),true);}
    public synchronized void accept(String uuid){try{Contact contact=store.contact(uuid);if(contact==null||!contact.state.equals("incoming"))throw new IllegalArgumentException("好友申请已失效");store.transaction(()->{store.saveContact(contact.profile,"accepted",contact.request);queueResponse(contact,"ACCEPT");});subscribe(store.contact(uuid));flush();status="已添加 "+contact.profile.name;changed();}catch(Exception e){error(e);}}
    public synchronized void decline(String uuid){try{Contact contact=store.contact(uuid);if(contact==null||!contact.state.equals("incoming"))return;store.transaction(()->{store.saveContact(contact.profile,"declined",contact.request);queueResponse(contact,"REJECT");});flush();status="已拒绝好友申请";changed();}catch(Exception e){error(e);}}
    public synchronized boolean send(String uuid,String text){try{Contact contact=store.contact(uuid);if(contact==null||!contact.state.equals("accepted"))throw new IllegalArgumentException("双方成为好友后才可私聊");String id=UUID.randomUUID().toString();byte[] body=packet("CHAT",id,uuid,"",PeerProtocol.encrypt(contact.profile,text));store.transaction(()->{store.addMessage(id,uuid,text,true,System.currentTimeMillis(),"待发送");store.enqueue(id,PeerProtocol.room(self.uuid,uuid),body,false);});flush();changed();return true;}catch(Exception e){error(e);return false;}}
    private void flush()throws Exception{if(!connected())return;List<Pending> batch=store.pending(outboxCursor);if(batch.isEmpty()){outboxCursor="";batch=store.pending("");}
        for(Pending pending:batch){if(!sending.contains(pending.id)){if(!client.publish(pending.id,pending.topic,pending.bytes,pending.retain))break;sending.add(pending.id);}outboxCursor=pending.id;}}
    private void receive(String topic,byte[] body,boolean retained)throws Exception{
        if(body.length==0)return;Envelope envelope=PeerProtocol.decode(body);Profile sender=envelope.from;if(sender.uuid.equals(self.uuid))return;
        if(topic.startsWith(PeerProtocol.ROOT+"directory/")){
            String code=sender.code();Lookup lookup=lookups.get(code);if(lookup==null||!topic.equals(PeerProtocol.directory(code))||!envelope.type.equals("PROFILE")||(!lookup.uuid.isEmpty()&&!lookup.uuid.equals(sender.uuid)))return;
            Contact old=store.contact(sender.uuid);if(old!=null&&old.state.equals("accepted")){status="对方已是好友";}else{String request=UUID.randomUUID().toString();store.transaction(()->{store.saveContact(sender,"outgoing",request);queueRequest(sender,request);});subscribe(store.contact(sender.uuid));status="好友申请已发送给 "+sender.name;flush();}
            lookups.remove(code);client.unsubscribe(topic);changed();return;
        }
        Contact contact=store.contact(sender.uuid);if(contact!=null&&!Arrays.equals(contact.profile.key,sender.key))return;
        if(topic.equals(PeerProtocol.status(sender.uuid))&&envelope.type.equals("PRESENCE")){
            if(contact==null||contact.state.equals("declined"))return;long now=System.currentTimeMillis();if(envelope.time>now+120000)return;Presence old=presence.get(sender.uuid);
            if(envelope.body.equals("offline")){if(old==null||old.session.equals(envelope.ref))presence.put(sender.uuid,new Presence(envelope.ref,envelope.time,false));}
            else if(envelope.body.equals("online")&&(old==null||envelope.time>=old.timestamp))presence.put(sender.uuid,new Presence(envelope.ref,envelope.time,true));
            store.saveContact(sender,contact.state,contact.request);changed();return;
        }
        if(!envelope.to.equals(self.uuid))return;
        if(topic.equals(PeerProtocol.inbox(self.uuid,sender.uuid))){
            if(envelope.type.equals("REQUEST")&&envelope.id.equals(envelope.ref)){
                if(contact!=null&&contact.state.equals("accepted")){store.saveContact(sender,"accepted",envelope.ref);queueResponse(store.contact(sender.uuid),"ACCEPT");}
                else if(contact==null||!contact.request.equals(envelope.ref)){store.saveContact(sender,"incoming",envelope.ref);subscribe(store.contact(sender.uuid));status=sender.name+" 请求添加你为好友";}
                flush();changed();
            }else if(contact!=null&&contact.state.equals("outgoing")&&contact.request.equals(envelope.ref)){
                if(envelope.type.equals("ACCEPT")){store.saveContact(sender,"accepted",contact.request);subscribe(store.contact(sender.uuid));status=sender.name+" 已同意好友申请";}
                else if(envelope.type.equals("REJECT")){store.saveContact(sender,"declined",contact.request);status=sender.name+" 拒绝了好友申请";}else return;
                client.publish("clear-"+UUID.randomUUID(),PeerProtocol.inbox(sender.uuid,self.uuid),new byte[0],true);changed();
            }return;
        }
        if(contact==null||!contact.state.equals("accepted")||!topic.equals(PeerProtocol.room(self.uuid,sender.uuid))||retained)return;
        if(envelope.type.equals("CHAT")){
            String text=PeerProtocol.decrypt(self,envelope.body);String ack=UUID.randomUUID().toString();byte[] receipt=packet("DELIVERED",ack,sender.uuid,envelope.id,"");
            store.transaction(()->{store.addMessage(envelope.id,sender.uuid,text,false,envelope.time,"已收到");store.enqueue(ack,topic,receipt,false);});status=sender.name+" 发来消息";flush();changed();
        }else if(envelope.type.equals("DELIVERED")){store.delivered(PeerProtocol.id(envelope.ref),sender.uuid);changed();}
    }
    public synchronized void exportBackup(Path path){try{store.exportBackup(path);status="备份已导出："+path;changed();}catch(Exception e){error(e);}}
    public synchronized void importBackup(Path path){disconnect();try{store.importBackup(path);self=store.identity();status="身份和好友已恢复，请连接网络";changed();}catch(Exception e){error(e);}}
    @Override public synchronized void close(){if(closed)return;disconnect();closed=true;timer.shutdownNow();try{store.close();}catch(Exception ignored){}}
}
