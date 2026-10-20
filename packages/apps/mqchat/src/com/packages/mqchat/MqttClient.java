package com.packages.mqchat;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** Small MQTT 3.1.1 client: exact-topic subscription, QoS 1, clean sessions and bounded queues.
 * One daemon owns protocol state. Socket timeouts never discard a partially received packet. */
public final class MqttClient implements AutoCloseable {
    public interface Listener {
        void state(boolean ready, String detail);
        void message(byte[] payload);
        void published(String id);
        void failed(String id);
    }
    static final int MAX_PACKET = 65536;
    private final String host, topic, clientId = "mqc" + UUID.randomUUID().toString().replace("-", "").substring(0,20);
    private final int port;
    private final boolean tls;
    private final Listener listener;
    private final BlockingQueue<Outgoing> queue = new ArrayBlockingQueue<>(64);
    private final Map<Integer,Outgoing> pending = new HashMap<>();
    private final Semaphore slots = new Semaphore(64);
    private volatile Socket socket;
    private volatile boolean closed, ready;
    private final Thread worker;
    private int packetId = 1;
    private long lastWrite, pingSent;
    private static final class Outgoing {
        final String id; final byte[] payload; long sent;
        Outgoing(String id, byte[] payload) { this.id=id;this.payload=payload.clone(); }
    }
    public MqttClient(String host,int port,boolean tls,String topic,Listener listener) {
        validateTopic(topic);
        if(host==null||host.trim().isEmpty()||port<1||port>65535)throw new IllegalArgumentException("Invalid broker");
        this.host=host;this.port=port;this.tls=tls;this.topic=topic;this.listener=Objects.requireNonNull(listener);
        worker=new Thread(this::run,"mqchat-mqtt");worker.setDaemon(true);
    }
    public void start() { worker.start(); }
    public boolean isReady() { return ready && !closed; }
    public synchronized boolean publish(String id,byte[] payload) {
        if(!isReady()||payload.length>16384||!slots.tryAcquire())return false;
        if(queue.offer(new Outgoing(id,payload)))return true;
        slots.release();return false;
    }
    public static void validateTopic(String topic) {
        if(topic==null||topic.trim().isEmpty()||topic.getBytes(StandardCharsets.UTF_8).length>512
                ||topic.indexOf('+')>=0||topic.indexOf('#')>=0||topic.startsWith("$share/")
                ||topic.codePoints().anyMatch(c->c==0||Character.isISOControl(c)||c>=0xd800&&c<=0xdfff))
            throw new IllegalArgumentException("Topic must be 1-512 UTF-8 bytes, without +, # or control characters");
    }
    private void run() {
        int delay=1;
        try {
            while(!closed) {
                listener.state(false,"Connecting...");
                try { connect(); delay=1; serve(); }
                catch(Exception error) {
                    if(!closed) listener.state(false,"Connection lost: "+brief(error)+"; retry in "+delay+"s");
                } finally { ready=false; disconnectSocket(); failPending(); }
                if(!closed) {
                    try { Thread.sleep(delay*1000L); } catch(InterruptedException ignored) {}
                    delay=Math.min(20,delay*2);
                }
            }
        } finally { ready=false;disconnectSocket();failPending(); }
    }
    private static String brief(Exception e) { String s=e.getMessage();return s==null?e.getClass().getSimpleName():s; }
    private void connect() throws Exception {
        Socket plain=new Socket();socket=plain;
        if(closed)throw new IOException("Closed");
        plain.connect(new InetSocketAddress(host,port),8000);plain.setSoTimeout(8000);plain.setTcpNoDelay(true);
        if(tls) {
            SSLSocket ssl=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(plain,host,port,true);
            socket=ssl;
            SSLParameters parameters=ssl.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");ssl.setSSLParameters(parameters);
            ssl.setSoTimeout(8000);ssl.startHandshake();
        }
        if(closed)throw new IOException("Closed");
        ByteArrayOutputStream data=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(data);
        utf(out,"MQTT");out.writeByte(4);out.writeByte(2);out.writeShort(40);utf(out,clientId);
        write(0x10,data.toByteArray());
        Packet conn=read(socket.getInputStream());
        if(conn==null||conn.header!=0x20||conn.data.length!=2||conn.data[0]!=0||conn.data[1]!=0)throw new IOException("MQTT connection refused");
        data.reset();out.writeShort(1);utf(out,topic);out.writeByte(1);write(0x82,data.toByteArray());
        socket.setSoTimeout(500);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!closed) {
            Packet packet=read(socket.getInputStream());
            if(packet==null){if(System.nanoTime()>deadline)throw new IOException("Subscription timeout");continue;}
            if(packet.header==0x90) {
                if(packet.data.length!=3||u16(packet.data,0)!=1||(packet.data[2]!=0&&packet.data[2]!=1))throw new IOException("Subscription rejected");
                ready=true;pingSent=0;listener.state(true,"Connected / subscribed");return;
            }
            if(packet.header>>4==3)receive(packet);
            else throw new IOException("Expected SUBACK");
        }
    }
    private void serve() throws Exception {
        InputStream input=socket.getInputStream();
        while(!closed) {
            Outgoing item;
            while((item=queue.poll())!=null) {
                int id=nextId();ByteArrayOutputStream data=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(data);
                utf(out,topic);out.writeShort(id);out.write(item.payload);item.sent=System.nanoTime();pending.put(id,item);
                write(0x32,data.toByteArray()); // QoS 1, retain=false, DUP=false
            }
            Packet packet=read(input);
            if(packet!=null) {
                if(packet.header>>4==3)receive(packet);
                else if(packet.header==0x40&&packet.data.length==2) {
                    Outgoing sent=pending.remove(u16(packet.data,0));if(sent!=null){slots.release();listener.published(sent.id);}
                } else if(packet.header==0xd0&&packet.data.length==0)pingSent=0;
                else throw new IOException("Unexpected MQTT packet");
            }
            long now=System.nanoTime();
            if(pingSent!=0&&now-pingSent>TimeUnit.SECONDS.toNanos(12))throw new IOException("Heartbeat timeout");
            for(Outgoing sent:pending.values())if(now-sent.sent>TimeUnit.SECONDS.toNanos(15))throw new IOException("Publish acknowledgement timeout");
            if(now-lastWrite>TimeUnit.SECONDS.toNanos(20)&&pingSent==0){write(0xc0,new byte[0]);pingSent=now;}
        }
    }
    private int nextId() throws IOException {
        for(int i=0;i<65535;i++){packetId=packetId%65535+1;if(!pending.containsKey(packetId))return packetId;}
        throw new IOException("Too many pending messages");
    }
    private void receive(Packet packet) throws Exception {
        int qos=(packet.header>>1)&3;
        if(qos>1||packet.data.length<2)throw new IOException("Unsupported PUBLISH");
        int len=u16(packet.data,0),offset=2+len;
        if(offset+(qos==1?2:0)>packet.data.length)throw new IOException("Invalid PUBLISH");
        String incoming=decode(Arrays.copyOfRange(packet.data,2,offset));
        int id=0;if(qos==1){id=u16(packet.data,offset);offset+=2;if(id==0)throw new IOException("Invalid packet identifier");}
        if(incoming.equals(topic)&&(packet.header&1)==0&&packet.data.length-offset<=16384)
            listener.message(Arrays.copyOfRange(packet.data,offset,packet.data.length));
        if(qos==1)write(0x40,new byte[]{(byte)(id>>8),(byte)id});
    }
    private void write(int header,byte[] bytes)throws IOException {
        OutputStream output=socket.getOutputStream();output.write(header);int n=bytes.length;
        do{int b=n%128;n/=128;output.write(n>0?b|128:b);}while(n>0);
        output.write(bytes);output.flush();lastWrite=System.nanoTime();
    }
    private void failPending() {
        synchronized(this){ready=false;}
        for(Outgoing item:pending.values()){slots.release();listener.failed(item.id);}pending.clear();
        Outgoing item;while((item=queue.poll())!=null){slots.release();listener.failed(item.id);}
    }
    private void disconnectSocket() {Socket s=socket;socket=null;if(s!=null)try{s.close();}catch(IOException ignored){} }
    @Override public void close() {closed=true;ready=false;disconnectSocket();worker.interrupt();}
    static void utf(DataOutputStream out,String text)throws IOException {byte[] bytes=text.getBytes(StandardCharsets.UTF_8);out.writeShort(bytes.length);out.write(bytes);}
    static int u16(byte[] data,int offset){return ((data[offset]&255)<<8)|(data[offset+1]&255);}
    static String decode(byte[] bytes)throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    static final class Packet {final int header;final byte[] data;Packet(int h,byte[] d){header=h;data=d;} }
    static Packet read(InputStream input)throws IOException {
        int header;
        try{header=input.read();}catch(SocketTimeoutException idle){return null;}
        if(header<0)throw new EOFException("Broker closed connection");
        int length=0,multiplier=1;
        for(int i=0;i<4;i++){
            int b=required(input);length+=(b&127)*multiplier;
            if(length>MAX_PACKET)throw new IOException("MQTT packet too large");
            if((b&128)==0){byte[] data=new byte[length];for(int j=0;j<length;j++)data[j]=(byte)required(input);return new Packet(header,data);}
            multiplier*=128;
        }
        throw new IOException("Invalid MQTT remaining length");
    }
    // Time out a stalled partial packet by reconnecting, instead of treating its suffix as a new header.
    private static int required(InputStream input)throws IOException {int b=input.read();if(b<0)throw new EOFException("Truncated MQTT packet");return b;}
}