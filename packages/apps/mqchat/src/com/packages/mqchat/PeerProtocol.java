package com.packages.mqchat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/** Versioned, signed envelopes and recipient-only encrypted chat bodies. */
public final class PeerProtocol {
    public static final String ROOT="mqchat/p2p/v2/";
    private static final SecureRandom RANDOM=new SecureRandom();
    public static class Profile {
        public final String uuid,name; public final byte[] avatar,key;
        public Profile(String uuid,String name,byte[] avatar,byte[] key) {
            this.uuid=id(uuid);this.name=Objects.requireNonNull(name).trim();
            if(this.name.isEmpty()||this.name.codePointCount(0,this.name.length())>24||this.name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("昵称需为 1–24 个字符");
            if(avatar.length>6000||key.length>1024)throw new IllegalArgumentException("资料过大");
                        if(avatar.length>0)try(javax.imageio.stream.ImageInputStream stream=new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(avatar))){
                Iterator<javax.imageio.ImageReader> readers=javax.imageio.ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IOException("头像格式无效");javax.imageio.ImageReader reader=readers.next();try{reader.setInput(stream);if(reader.getWidth(0)>256||reader.getHeight(0)>256)throw new IOException("头像尺寸过大");}finally{reader.dispose();}
            }catch(IOException e){throw new IllegalArgumentException("头像无效",e);}
            this.avatar=avatar.clone();this.key=key.clone();publicKey(this.key);
        }
        public String code(){return shortCode(uuid);}
    }
    public static final class Identity extends Profile {
        public final byte[] secret;
        public Identity(String uuid,String name,byte[] avatar,byte[] key,byte[] secret){super(uuid,name,avatar,key);this.secret=secret.clone();
            try {Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(privateKey(secret));signature.update(key);byte[] proof=signature.sign();signature.initVerify(publicKey(key));signature.update(key);if(!signature.verify(proof))throw new IllegalArgumentException("身份密钥不匹配");}
            catch(GeneralSecurityException e){throw new IllegalArgumentException("身份密钥无效",e);}}
        public static Identity create(){try{KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);KeyPair pair=generator.generateKeyPair();String uuid=UUID.randomUUID().toString();return new Identity(uuid,"用户-"+shortCode(uuid).substring(0,4),new byte[0],pair.getPublic().getEncoded(),pair.getPrivate().getEncoded());}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
    }
    public static final class Envelope {
        public final String type,id,to,ref,body;public final long time;public final Profile from;
        Envelope(String type,String id,String to,String ref,String body,long time,Profile from){this.type=type;this.id=id;this.to=to;this.ref=ref;this.body=body;this.time=time;this.from=from;}
    }
    public static String id(String value){String canonical=UUID.fromString(value).toString();if(!canonical.equals(value))throw new IllegalArgumentException("UUID 格式无效");return canonical;}
    public static String hash(String value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder text=new StringBuilder();for(byte b:bytes)text.append(String.format(Locale.ROOT,"%02x",b&255));return text.toString();}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
    public static String shortCode(String uuid){byte[] bytes;try{bytes=MessageDigest.getInstance("SHA-256").digest(id(uuid).getBytes(StandardCharsets.UTF_8));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}String alphabet="0123456789ABCDEFGHJKMNPQRSTVWXYZ";StringBuilder result=new StringBuilder();int bits=0,buffer=0;for(byte b:bytes){buffer=(buffer<<8)|(b&255);bits+=8;while(bits>=5&&result.length()<13){bits-=5;result.append(alphabet.charAt((buffer>>>bits)&31));}if(result.length()==13)break;}return result.toString();}
    public static String status(String uuid){return ROOT+"users/"+id(uuid)+"/status";}
    public static String inbox(String uuid){return ROOT+"users/"+id(uuid)+"/inbox/+";}
    public static String inbox(String to,String from){return ROOT+"users/"+id(to)+"/inbox/"+id(from);}
    public static String directory(String code){if(!code.matches("[0-9A-HJKMNP-TV-Z]{13}"))throw new IllegalArgumentException("请输入 UUID 或 13 位展示码");return ROOT+"directory/"+code;}
    public static String room(String a,String b){id(a);id(b);return ROOT+"chat/"+hash(a.compareTo(b)<0?a+":"+b:b+":"+a);}
    public static byte[] encode(Identity identity,String type,String messageId,String to,String ref,String body)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(0x4d515032);out.writeUTF(type);out.writeUTF(id(messageId));out.writeUTF(to);out.writeUTF(ref);out.writeLong(System.currentTimeMillis());
        out.writeUTF(identity.uuid);out.writeUTF(identity.name);blob(out,identity.avatar);blob(out,identity.key);out.writeUTF(body);
        byte[] signed=bytes.toByteArray();Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(privateKey(identity.secret));signature.update(signed);blob(out,signature.sign());
        if(bytes.size()>16384)throw new IllegalArgumentException("消息过大");return bytes.toByteArray();
    }
    public static Envelope decode(byte[] bytes)throws Exception{
        if(bytes.length>16384)throw new IOException("Envelope too large");ByteArrayInputStream buffer=new ByteArrayInputStream(bytes);DataInputStream in=new DataInputStream(buffer);
        if(in.readInt()!=0x4d515032)throw new IOException("Wrong protocol");String type=in.readUTF(),messageId=id(in.readUTF()),to=in.readUTF(),ref=in.readUTF();long time=in.readLong();
        Profile profile=new Profile(in.readUTF(),in.readUTF(),blob(in,6000),blob(in,1024));String body=in.readUTF();int signedLength=bytes.length-buffer.available();byte[] proof=blob(in,512);
        if(buffer.available()!=0)throw new IOException("Trailing data");Signature signature=Signature.getInstance("SHA256withRSA");signature.initVerify(publicKey(profile.key));signature.update(bytes,0,signedLength);if(!signature.verify(proof))throw new SignatureException("Bad signature");
        return new Envelope(type,messageId,to,ref,body,time,profile);
    }
    public static String encrypt(Profile recipient,String body)throws Exception{
        if(body.trim().isEmpty()||body.codePointCount(0,body.length())>2000)throw new IllegalArgumentException("消息需为 1–2000 字符");
        byte[] key=new byte[16],nonce=new byte[12];RANDOM.nextBytes(key);RANDOM.nextBytes(nonce);
        Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");aes.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        Cipher rsa=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");rsa.init(Cipher.ENCRYPT_MODE,publicKey(recipient.key),oaep());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);blob(out,rsa.doFinal(key));blob(out,nonce);blob(out,aes.doFinal(body.getBytes(StandardCharsets.UTF_8)));return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
    public static String decrypt(Identity identity,String body)throws Exception{
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(body)));byte[] wrapped=blob(in,512),nonce=blob(in,12),encrypted=blob(in,10000);if(nonce.length!=12||in.available()!=0)throw new IOException("Invalid encrypted body");
        Cipher rsa=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");rsa.init(Cipher.DECRYPT_MODE,privateKey(identity.secret),oaep());byte[] key=rsa.doFinal(wrapped);
        Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");aes.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));String value=MqttClient.decode(aes.doFinal(encrypted));if(value.codePointCount(0,value.length())>2000)throw new IOException("Body too large");return value;
    }
    private static OAEPParameterSpec oaep(){return new OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT);}
    private static PublicKey publicKey(byte[] key){try{PublicKey result=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key));if(((java.security.interfaces.RSAPublicKey)result).getModulus().bitLength()!=2048)throw new IllegalArgumentException("需要 RSA-2048 身份密钥");return result;}catch(GeneralSecurityException e){throw new IllegalArgumentException("公钥无效",e);}}
    private static PrivateKey privateKey(byte[] key)throws GeneralSecurityException{return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key));}
    private static void blob(DataOutputStream out,byte[] bytes)throws IOException{out.writeInt(bytes.length);out.write(bytes);}
    private static byte[] blob(DataInputStream in,int limit)throws IOException{int length=in.readInt();if(length<0||length>limit)throw new IOException("Invalid field size");byte[] value=new byte[length];in.readFully(value);return value;}
}
