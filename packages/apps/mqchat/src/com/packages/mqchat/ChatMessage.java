package com.packages.mqchat;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned UTF-8 envelope; ordinary MQTT text is also accepted for MQTTX interoperability. */
public final class ChatMessage {
    static final String PREFIX="MQCHAT/1\n";
    public final String id,sender,name,body;
    public final long time;
    public final boolean own;
    public String status;
    private ChatMessage(String id,String sender,String name,String body,long time,boolean own,String status){
        this.id=id;this.sender=sender;this.name=name;this.body=body;this.time=time;this.own=own;this.status=status;
    }
    public static ChatMessage outgoing(String sender,String name,String body){
        validate(name,body);
        return new ChatMessage(UUID.randomUUID().toString(),sender,name,body,System.currentTimeMillis(),true,"发送中");
    }
    public static void validate(String name,String body){
        if(name==null||name.trim().isEmpty()||name.codePointCount(0,name.length())>24||bad(name,false))throw new IllegalArgumentException("昵称需为 1–24 个字符");
        if(body==null||body.trim().isEmpty()||body.codePointCount(0,body.length())>2000||bad(body,true))throw new IllegalArgumentException("消息需为 1–2000 个字符");
    }
    private static boolean bad(String value,boolean lines){return value.codePoints().anyMatch(c->(Character.isISOControl(c)&&!(lines&&c=='\n'))||c>=0xd800&&c<=0xdfff);}
    private static String encode(String value){return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
    private static String decode(String value)throws Exception{return MqttClient.decode(Base64.getDecoder().decode(value));}
    public byte[] bytes(){return (PREFIX+id+"\n"+sender+"\n"+time+"\n"+encode(name)+"\n"+encode(body)).getBytes(StandardCharsets.UTF_8);}
    public static ChatMessage incoming(byte[] bytes,String localSender){
        try{
            if(bytes.length>16384)return null;
            String value=MqttClient.decode(bytes);
            if(!value.startsWith(PREFIX)){
                validate("MQTT 用户",value);
                return new ChatMessage(UUID.randomUUID().toString(),"external","MQTT 用户",value,System.currentTimeMillis(),false,"收到");
            }
            String[] parts=value.substring(PREFIX.length()).split("\n",-1);
            if(parts.length!=5)return null;
            UUID.fromString(parts[0]);UUID.fromString(parts[1]);
            long time=Long.parseLong(parts[2]);if(time<0||time>253402300799000L)return null;
            String name=decode(parts[3]),body=decode(parts[4]);validate(name,body);
            return new ChatMessage(parts[0],parts[1],name,body,time,parts[1].equals(localSender),"收到");
        }catch(Exception invalid){return null;}
    }
}