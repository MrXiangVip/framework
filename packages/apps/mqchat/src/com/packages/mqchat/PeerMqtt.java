package com.packages.mqchat;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** Multi-topic QoS1 transport. Re-subscribes desired filters after every reconnect. */
public final class PeerMqtt implements AutoCloseable {
    public interface Listener {void state(boolean ready,String detail);void message(String topic,byte[] body,boolean retained);void acknowledged(String id);}
    private final String host,willTopic,clientId;private final int port;private final boolean tls;private final byte[] will;private final Listener listener;
    private final Set<String> desired=new ConcurrentSkipListSet<>();
    private final BlockingQueue<Command> commands=new ArrayBlockingQueue<>(256);
    private final Map<Integer,String> publishes=new HashMap<>();private final Map<Integer,Long> subscriptions=new HashMap<>();
    private volatile boolean closed,ready;private volatile Socket socket;private final Thread worker;private int sequence=1;private long lastWrite,ping;
    private interface Command {void run()throws Exception;}
    public PeerMqtt(String host,int port,boolean tls,String clientId,String willTopic,byte[] will,Listener listener){this.host=host;this.port=port;this.tls=tls;this.clientId=clientId;this.willTopic=willTopic;this.will=will.clone();this.listener=listener;worker=new Thread(this::loop,"mqchat-peer-mqtt");worker.setDaemon(true);}
    public void start(){worker.start();}
    public boolean ready(){return ready&&!closed;}
    public void subscribe(String filter){validateFilter(filter);if(desired.add(filter)&&(ready||socket!=null)&&!commands.offer(()->subscription(filter,false)))drop();}
    public void unsubscribe(String filter){if(desired.remove(filter)&&ready&&!commands.offer(()->subscription(filter,true)))drop();}
    public boolean publish(String id,String topic,byte[] body,boolean retained){MqttClient.validateTopic(topic);if(body.length>16384||!ready())return false;byte[] copy=body.clone();return commands.offer(()->{if(publishes.containsValue(id))return;int packetId=next();ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);MqttClient.utf(out,topic);out.writeShort(packetId);out.write(copy);publishes.put(packetId,id);write(retained?0x33:0x32,bytes.toByteArray());});}
    private void loop(){int delay=1;while(!closed){try{listener.state(false,"正在连接 EMQX…");connect();delay=1;serve();}catch(Exception error){if(!closed)listener.state(false,"连接中断，正在重连："+(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()));}finally{ready=false;drop();commands.clear();publishes.clear();subscriptions.clear();}if(!closed)try{Thread.sleep(delay*1000L);delay=Math.min(20,delay*2);}catch(InterruptedException ignored){}}}
    private void connect()throws Exception{
        Socket plain=new Socket();socket=plain;if(closed)throw new IOException("Closed");plain.connect(new InetSocketAddress(host,port),8000);plain.setSoTimeout(8000);plain.setTcpNoDelay(true);
        if(tls){SSLSocket ssl=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(plain,host,port,true);socket=ssl;SSLParameters parameters=ssl.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");ssl.setSSLParameters(parameters);ssl.startHandshake();}
        if(closed)throw new IOException("Closed");ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);MqttClient.utf(out,"MQTT");out.writeByte(4);out.writeByte(0x2e);out.writeShort(30);MqttClient.utf(out,clientId);MqttClient.utf(out,willTopic);out.writeShort(will.length);out.write(will);write(0x10,bytes.toByteArray());
        MqttClient.Packet packet=MqttClient.read(socket.getInputStream());if(packet==null||packet.header!=0x20||packet.data.length!=2||packet.data[1]!=0)throw new IOException("Broker refused connection");
        for(String filter:desired)subscription(filter,false);socket.setSoTimeout(200);
        long deadline=System.currentTimeMillis()+10000;while(!subscriptions.isEmpty()){packet=MqttClient.read(socket.getInputStream());if(packet!=null)receive(packet);if(System.currentTimeMillis()>deadline)throw new IOException("订阅超时");}
        ready=true;ping=0;listener.state(true,"已连接 EMQX");
    }
    private void subscription(String filter,boolean remove)throws Exception{int id=next();ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeShort(id);MqttClient.utf(out,filter);if(!remove)out.writeByte(1);subscriptions.put(id,System.currentTimeMillis());write(remove?0xa2:0x82,bytes.toByteArray());}
    private void serve()throws Exception{while(!closed){for(int i=0;i<32&&publishes.size()<128;i++){Command command=commands.poll();if(command==null)break;command.run();}MqttClient.Packet packet=MqttClient.read(socket.getInputStream());if(packet!=null)receive(packet);long now=System.currentTimeMillis();if(ping!=0&&now-ping>12000)throw new IOException("MQTT 心跳超时");for(long sent:subscriptions.values())if(now-sent>10000)throw new IOException("订阅确认超时");if(!publishes.isEmpty()&&now-lastWrite>15000)throw new IOException("发布确认超时");if(now-lastWrite>10000&&ping==0){write(0xc0,new byte[0]);ping=now;}}}
    private void receive(MqttClient.Packet packet)throws Exception{
        int type=packet.header>>4;
        if(type==3){int qos=(packet.header>>1)&3;if(qos>1||packet.data.length<2)throw new IOException("Invalid publish");int length=MqttClient.u16(packet.data,0),offset=2+length;if(offset+(qos==1?2:0)>packet.data.length)throw new IOException("Invalid topic");String topic=MqttClient.decode(Arrays.copyOfRange(packet.data,2,offset));int id=0;if(qos==1){id=MqttClient.u16(packet.data,offset);offset+=2;}if(packet.data.length-offset<=16384)listener.message(topic,Arrays.copyOfRange(packet.data,offset,packet.data.length),(packet.header&1)!=0);if(qos==1)write(0x40,new byte[]{(byte)(id>>8),(byte)id});}
        else if(packet.header==0x40&&packet.data.length==2){String id=publishes.remove(MqttClient.u16(packet.data,0));if(id!=null)listener.acknowledged(id);}
        else if(packet.header==0x90&&packet.data.length==3){if(packet.data[2]!=0&&packet.data[2]!=1)throw new IOException("订阅被拒绝");subscriptions.remove(MqttClient.u16(packet.data,0));}
        else if(packet.header==0xb0&&packet.data.length==2)subscriptions.remove(MqttClient.u16(packet.data,0));
        else if(packet.header==0xd0&&packet.data.length==0)ping=0;
        else throw new IOException("Invalid MQTT packet");
    }
    private int next()throws IOException{for(int i=0;i<65535;i++){sequence=sequence%65535+1;if(!publishes.containsKey(sequence)&&!subscriptions.containsKey(sequence))return sequence;}throw new IOException("Too many packets");}
    private void write(int header,byte[] bytes)throws IOException{OutputStream out=socket.getOutputStream();out.write(header);int n=bytes.length;do{int b=n%128;n/=128;out.write(n>0?b|128:b);}while(n>0);out.write(bytes);out.flush();lastWrite=System.currentTimeMillis();}
    static void validateFilter(String filter){if(filter.endsWith("/+"))MqttClient.validateTopic(filter.substring(0,filter.length()-2));else MqttClient.validateTopic(filter);}
    private void drop(){Socket active=socket;socket=null;if(active!=null)try{active.close();}catch(IOException ignored){}}
    @Override public void close(){closed=true;ready=false;drop();worker.interrupt();}
}
