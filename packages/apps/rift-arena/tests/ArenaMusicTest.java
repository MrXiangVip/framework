package com.packages.riftarena;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

public final class ArenaMusicTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void waitFor(BooleanSupplier condition)throws Exception{
        long deadline=System.nanoTime()+3000000000L;
        while(!condition.getAsBoolean()){check(System.nanoTime()<deadline,"Audio timeout");Thread.sleep(5);}
    }
    public static void main(String[] args)throws Exception{
        ClassLoader loader=ArenaMusicTest.class.getClassLoader();
        for(String name:new String[]{"menu","battle","scatter-release","bow-release","arrow-hit","sword-swing","sword-hit","stone-hit","kill-hit","slam-charge","slam-impact","dash-whoosh","meteor-charge","meteor-impact"}){
            byte[] pcm=ArenaMusic.load(loader,name);check(pcm.length>22050*4*(name.equals("menu")||name.equals("battle")?30:.15),"Audio duration: "+name);
            long energy=0;int peak=0;
            for(int i=0;i<pcm.length;i+=2){int value=(short)((pcm[i]&255)|(pcm[i+1]<<8));energy+=(long)value*value;peak=Math.max(peak,Math.abs(value));}
            check(energy/((double)pcm.length/2)>10000&&peak<32760,"Audible nonclipping music: "+name);
        }
        byte[] loud={(byte)0x30,(byte)0x75,(byte)0xd0,(byte)0x8a};
        byte[] mixed=new byte[4];java.util.List<ArenaMusic.Voice> voices=new java.util.ArrayList<>();voices.add(new ArenaMusic.Voice(loud));
        ArenaMusic.mix(loud,0,4,voices,mixed);
        check((short)((mixed[0]&255)|(mixed[1]<<8))==32767&&(short)((mixed[2]&255)|(mixed[3]<<8))==-32768,"Mix saturates instead of wrapping");
        voices.get(0).position=4;ArenaMusic.mix(loud,0,4,voices,mixed);check(java.util.Arrays.equals(loud,mixed),"Finished effects contribute silence");
        AtomicInteger writes=new AtomicInteger(),flushes=new AtomicInteger();AtomicBoolean released=new AtomicBoolean();
        AtomicReference<byte[]> playing=new AtomicReference<>();
        ArenaMusic music=new ArenaMusic(loader,format->new ArenaMusic.Output(){
            public int write(byte[] pcm,int offset,int count){playing.set(pcm);writes.incrementAndGet();return count;}
            public void flush(){flushes.incrementAndGet();}
            public void close(){released.set(true);}
        });
        try{
            music.update(ArenaWorld.Screen.TITLE);waitFor(()->writes.get()>2);byte[] menu=playing.get();
            music.update(ArenaWorld.Screen.PLAYING);waitFor(()->playing.get()!=menu);
            byte[] battle=playing.get();music.playEffect("sword-hit");waitFor(()->playing.get()!=battle);
            waitFor(()->playing.get()==battle);
            music.playEffect("slam-charge");waitFor(()->playing.get()!=battle);music.stopEffect("slam-charge");waitFor(()->playing.get()==battle);
            int oldFlushes=flushes.get();final int beforePause=oldFlushes;music.update(ArenaWorld.Screen.PAUSED);waitFor(()->flushes.get()>beforePause);
            int paused=writes.get();Thread.sleep(40);check(writes.get()==paused,"Pause silences output");
            music.update(ArenaWorld.Screen.PLAYING);waitFor(()->writes.get()>paused);
            oldFlushes=flushes.get();music.toggleMuted();final int beforeMute=oldFlushes;waitFor(()->flushes.get()>beforeMute);
            int muted=writes.get();Thread.sleep(40);check(writes.get()==muted,"Mute silences output");
            music.toggleMuted();waitFor(()->writes.get()>muted);
        }finally{music.close();music.awaitClosed();}
        check(released.get(),"Close releases device");
        ArenaMusic missing=new ArenaMusic(loader,format->{throw new javax.sound.sampled.LineUnavailableException();});
        missing.update(ArenaWorld.Screen.PLAYING);waitFor(()->!missing.isAvailable());missing.close();missing.awaitClosed();
        System.out.println("PASS: original WAV decoding/levels, menu/battle switching, pause/resume, mute, device cleanup and unavailable-device fallback");
    }
}
