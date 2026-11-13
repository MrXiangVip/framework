package com.packages.riftarena;

import java.io.*;
import java.util.*;
import javax.sound.sampled.*;

/** Owns the audio device on one daemon. No device I/O on render or UI threads. */
final class ArenaMusic implements AutoCloseable {
    interface Output extends AutoCloseable {
        int write(byte[] pcm,int offset,int count);
        void flush();
        void close();
    }
    interface Factory { Output open(AudioFormat format)throws Exception; }
    private final ClassLoader loader;
    private final Factory factory;
    private final Object control=new Object();
    private volatile boolean closed,muted,unavailable;
    private volatile ArenaWorld.Screen screen=ArenaWorld.Screen.TITLE;
    private Thread worker;
    private final ArrayDeque<String> soundQueue=new ArrayDeque<>();
    private final Set<String> stoppedEffects=new HashSet<>();
    static final class Voice {final byte[] pcm;final String name;int position;Voice(byte[] pcm){this(null,pcm);}Voice(String name,byte[] pcm){this.name=name;this.pcm=pcm;}}
    void stopEffect(String name){synchronized(control){soundQueue.removeIf(name::equals);stoppedEffects.add(name);control.notifyAll();}}
    void playEffect(String name){
        if(!name.equals("scatter-release")&&!name.equals("bow-release")&&!name.equals("arrow-hit")&&!name.equals("meteor-charge")&&!name.equals("meteor-impact")&&!name.equals("sword-swing")&&!name.equals("sword-hit")&&!name.equals("stone-hit")&&!name.equals("kill-hit")&&!name.equals("slam-charge")&&!name.equals("slam-impact")&&!name.equals("dash-whoosh"))return;
        synchronized(control){
            if(closed||muted||unavailable||screen!=ArenaWorld.Screen.PLAYING)return;
            if(soundQueue.size()>=16)soundQueue.removeFirst();soundQueue.addLast(name);control.notifyAll();
        }
    }
    ArenaMusic(ClassLoader loader){this(loader,ArenaMusic::openDevice);}
    ArenaMusic(ClassLoader loader,Factory factory){this.loader=loader;this.factory=factory;}
    boolean isMuted(){return muted;}
    boolean isAvailable(){return !unavailable;}
    void toggleMuted(){synchronized(control){muted=!muted;soundQueue.clear();control.notifyAll();}}
    void update(ArenaWorld.Screen next){
        synchronized(control){
            if(closed||unavailable)return;
            boolean changed=screen!=next;screen=next;if(next!=ArenaWorld.Screen.PLAYING)soundQueue.clear();
            if(worker==null){worker=new Thread(this::run,"rift-arena-music");worker.setDaemon(true);worker.start();}
            if(changed)control.notifyAll();
        }
    }
    static byte[] load(ClassLoader loader,String name)throws Exception{
        try(InputStream resource=loader.getResourceAsStream("res/arena/audio/"+name+".wav")){
            if(resource==null)throw new IOException("Missing music: "+name);
            try(AudioInputStream stream=AudioSystem.getAudioInputStream(new BufferedInputStream(resource))){
                AudioFormat f=stream.getFormat();
                if(f.getSampleRate()!=22050||f.getSampleSizeInBits()!=16||f.getChannels()!=2||f.isBigEndian()||!AudioFormat.Encoding.PCM_SIGNED.equals(f.getEncoding()))throw new IOException("Unsupported music format");
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;
                while((n=stream.read(block))!=-1)bytes.write(block,0,n);
                byte[] data=bytes.toByteArray();if(data.length==0||data.length%4!=0)throw new IOException("Invalid music PCM");return data;
            }
        }
    }
    static void mix(byte[] music,int offset,int count,List<Voice> voices,byte[] out){
        for(int i=0;i<count;i+=2){
            int sample=(short)((music[offset+i]&255)|(music[offset+i+1]<<8));
            for(Voice voice:voices){int at=voice.position+i;if(at+1<voice.pcm.length)sample+=(short)((voice.pcm[at]&255)|(voice.pcm[at+1]<<8));}
            sample=Math.max(-32768,Math.min(32767,sample));out[i]=(byte)sample;out[i+1]=(byte)(sample>>8);
        }
    }
    private void run(){
        Output output=null;
        try{
            byte[][] tracks={load(loader,"menu"),load(loader,"battle")};int[] positions={0,0};int previous=-1;boolean silent=false;
            Map<String,byte[]> effects=new HashMap<>();effects.put("scatter-release",load(loader,"scatter-release"));effects.put("bow-release",load(loader,"bow-release"));effects.put("arrow-hit",load(loader,"arrow-hit"));effects.put("meteor-charge",load(loader,"meteor-charge"));effects.put("meteor-impact",load(loader,"meteor-impact"));effects.put("sword-swing",load(loader,"sword-swing"));effects.put("sword-hit",load(loader,"sword-hit"));effects.put("stone-hit",load(loader,"stone-hit"));effects.put("kill-hit",load(loader,"kill-hit"));effects.put("slam-charge",load(loader,"slam-charge"));effects.put("slam-impact",load(loader,"slam-impact"));effects.put("dash-whoosh",load(loader,"dash-whoosh"));
            List<Voice> voices=new ArrayList<>();byte[] mixed=new byte[1764];
            while(!closed){
                ArenaWorld.Screen state=screen;
                if(muted||state==ArenaWorld.Screen.PAUSED){
                    if(output!=null&&!silent)output.flush();silent=true;voices.clear();
                    synchronized(control){soundQueue.clear();if(!closed&&(muted||screen==ArenaWorld.Screen.PAUSED))control.wait(25);}continue;
                }
                int track=state==ArenaWorld.Screen.PLAYING?1:0;
                if(output==null)output=factory.open(new AudioFormat(22050,16,2,true,false));
                if(closed)break;
                if(previous!=track){output.flush();positions[track]=0;previous=track;voices.clear();}
                synchronized(control){voices.removeIf(voice->stoppedEffects.contains(voice.name));stoppedEffects.clear();while(!soundQueue.isEmpty()){String name=soundQueue.removeFirst();if(voices.size()>=4)voices.remove(0);voices.add(new Voice(name,effects.get(name)));}}
                silent=false;
                byte[] pcm=tracks[track];int offset=positions[track];
                if(!muted&&screen!=ArenaWorld.Screen.PAUSED){
                    int count=Math.min(mixed.length,pcm.length-offset),written;
                    if(voices.isEmpty())written=output.write(pcm,offset,count);
                    else{mix(pcm,offset,count,voices,mixed);written=output.write(mixed,0,count);}
                    if(written<0||written>count||written%4!=0)throw new IOException("Invalid audio write");
                    positions[track]=(offset+written)%pcm.length;
                    for(Iterator<Voice> it=voices.iterator();it.hasNext();){Voice voice=it.next();voice.position+=written;if(voice.position>=voice.pcm.length)it.remove();}
                }
                synchronized(control){if(!closed)control.wait(5);}
            }
        }catch(Exception failure){if(!closed){unavailable=true;System.err.println("Rift Arena music unavailable: "+failure.getClass().getSimpleName());}}
        finally{if(output!=null)try{output.flush();}finally{output.close();}}
    }
    private static Output openDevice(AudioFormat format)throws Exception{
        SourceDataLine line=(SourceDataLine)AudioSystem.getLine(new DataLine.Info(SourceDataLine.class,format));
        try{line.open(format,4096);line.start();}catch(Exception failure){line.close();throw failure;}
        return new Output(){
            public int write(byte[] pcm,int offset,int count){int available=Math.min(count,line.available())&~3;return available==0?0:line.write(pcm,offset,available);}
            public void flush(){line.flush();}
            public void close(){line.stop();line.close();}
        };
    }
    @Override public void close(){synchronized(control){closed=true;soundQueue.clear();control.notifyAll();}}
    void awaitClosed()throws InterruptedException{Thread active=worker;if(active!=null)active.join(2000);}
}
