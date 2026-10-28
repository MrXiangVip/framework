package com.packages.kof;

import com.android.content.Context;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.IOException;
import java.util.EnumMap;
import javax.imageio.ImageIO;

/** Eight supplied sprite strips. Sources are retained unchanged; all preparation is in memory. */
final class SunWukongSprites {
    enum Action { IDLE, WALK, JUMP, CROUCH, LIGHT_PUNCH, LIGHT_KICK, HEAVY_PUNCH, HEAVY_KICK }
    private static final class Frame {
        final BufferedImage image;
        final Rectangle bounds;
        final int pivot, floor;
        Frame(BufferedImage image) {
            this.image=image;
            int l=image.getWidth(),r=0,t=image.getHeight(),b=0;
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)>48){l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);b=Math.max(b,y);}
            if(l>r||t>b)throw new IllegalArgumentException("Empty Sun Wukong frame");
            bounds=new Rectangle(l,t,r-l+1,b-t+1);floor=b+1;
            long sum=0,count=0;
            for(int y=Math.max(t,b-9);y<=b;y++)for(int x=l;x<=r;x++)if((image.getRGB(x,y)>>>24)>128){sum+=x;count++;}
            pivot=count==0?(l+r)/2:(int)(sum/count);
        }
    }
    private static final class Clip {
        final Frame[] frames; final double scale;
        Clip(BufferedImage image,int[] cuts,int referenceHeight) {
            frames=new Frame[cuts.length-1];scale=185.0/referenceHeight;
            for(int i=0;i<frames.length;i++)frames[i]=new Frame(referenceHeight==320?image.getSubimage(cuts[i],0,cuts[i+1]-cuts[i],image.getHeight()):mainFigure(image.getSubimage(cuts[i],0,cuts[i+1]-cuts[i],image.getHeight())));
        }
    }
    private final EnumMap<Action,Clip> clips=new EnumMap<>(Action.class);
    private Action action=Action.IDLE;
    private double age, attackTime;
    SunWukongSprites(Context context) {
        for(Action a:Action.values()) {
            String name=a.name().toLowerCase(java.util.Locale.ROOT);
            BufferedImage image;
            try(InputStream input=context.getClassLoader().getResourceAsStream("res/drawable/sunwukong/"+name+".png")) {
                if(input==null)throw new IllegalStateException("Missing Sun Wukong sprite: "+name);
                image=ImageIO.read(input);if(image==null)throw new IOException("Invalid PNG");
            }catch(IOException error){throw new IllegalStateException("Cannot load Sun Wukong sprite: "+name,error);}
            image=removeMatte(image);
            int[] cuts;
            int reference=320;
            if(a==Action.HEAVY_KICK){cuts=new int[]{0,720,1440,2240,3070,3660,4352};reference=520;}
            else if(a==Action.LIGHT_KICK){cuts=new int[]{0,490,980,1575,2200,2570,3104};reference=640;}
            else {cuts=new int[9];for(int i=0;i<=8;i++)cuts[i]=i*image.getWidth()/8;}
            clips.put(a,new Clip(image,cuts,reference));
        }
    }
    /** Irregular kick strips can contain a fragment of the adjacent pose near a cut. */
    private static BufferedImage mainFigure(BufferedImage image) {
        int w=image.getWidth(),h=image.getHeight(),largest=0,best=0,id=0;
        int[] pixels=image.getRGB(0,0,w,h,null,0,w),labels=new int[w*h],queue=new int[w*h];
        for(int start=0;start<pixels.length;start++) {
            if(labels[start]!=0||(pixels[start]>>>24)<=8)continue;
            int head=0,tail=0;id++;labels[start]=id;queue[tail++]=start;
            while(head<tail){int i=queue[head++],x=i%w,y=i/w;
                if(x>0)tail=label(pixels,labels,queue,tail,i-1,id);if(x+1<w)tail=label(pixels,labels,queue,tail,i+1,id);
                if(y>0)tail=label(pixels,labels,queue,tail,i-w,id);if(y+1<h)tail=label(pixels,labels,queue,tail,i+w,id);
            }
            if(tail>best){best=tail;largest=id;}
        }
        for(int i=0;i<pixels.length;i++)if(labels[i]!=largest)pixels[i]=0;
        BufferedImage result=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);result.setRGB(0,0,w,h,pixels,0,w);return result;
    }
    private static int label(int[] pixels,int[] labels,int[] queue,int tail,int i,int id) {
        if(labels[i]==0&&(pixels[i]>>>24)>8){labels[i]=id;queue[tail++]=i;}return tail;
    }
    private static boolean matte(int c) {
        int r=c>>16&255,g=c>>8&255,b=c&255;
        return Math.min(r,Math.min(g,b))>150&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<32;
    }
    private static int enqueue(int[] pixels,int[] queue,int tail,int i) {
        if((pixels[i]>>>24)!=0&&matte(pixels[i])){pixels[i]=0;queue[tail++]=i;}return tail;
    }
    private static BufferedImage removeMatte(BufferedImage image) {
        if(image.getColorModel().hasAlpha())return image;
        int w=image.getWidth(),h=image.getHeight(),head=0,tail=0;
        int[] pixels=image.getRGB(0,0,w,h,null,0,w),queue=new int[w*h];
        for(int x=0;x<w;x++){tail=enqueue(pixels,queue,tail,x);tail=enqueue(pixels,queue,tail,(h-1)*w+x);}
        for(int y=1;y<h-1;y++){tail=enqueue(pixels,queue,tail,y*w);tail=enqueue(pixels,queue,tail,y*w+w-1);}
        while(head<tail){int i=queue[head++],x=i%w,y=i/w;
            if(x>0)tail=enqueue(pixels,queue,tail,i-1);if(x+1<w)tail=enqueue(pixels,queue,tail,i+1);
            if(y>0)tail=enqueue(pixels,queue,tail,i-w);if(y+1<h)tail=enqueue(pixels,queue,tail,i+w);
        }
        BufferedImage clean=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);clean.setRGB(0,0,w,h,pixels,0,w);return clean;
    }
    void reset(){action=Action.IDLE;age=attackTime=0;}
    void update(FightEngine.Fighter fighter,double dt) {
        Action next=Action.IDLE;
        FightEngine.Move move=fighter.getMove();
        if(move!=null) switch(move) {
            case LIGHT_PUNCH:next=Action.LIGHT_PUNCH;break;case LIGHT_KICK:next=Action.LIGHT_KICK;break;
            case PUNCH:next=Action.HEAVY_PUNCH;break;case KICK:next=Action.HEAVY_KICK;break;default:break;
        }
        else if(fighter.getHeight()>0)next=Action.JUMP;
        else if(fighter.isCrouching())next=Action.CROUCH;
        else if(fighter.isMoving())next=Action.WALK;
        if(next!=action || (move!=null&&fighter.getAttackTime()<attackTime))age=0;
        else age+=Math.max(0,Math.min(.25,dt));
        action=next;attackTime=fighter.getAttackTime();
    }
    Action getAction(){return action;}
    int frameCount(Action a){return clips.get(a).frames.length;}
    int frameIndex(FightEngine.Fighter fighter) {
        int n=clips.get(action).frames.length;FightEngine.Move move=fighter==null?null:fighter.getMove();
        if(move!=null) {
            double t=fighter.getAttackTime(),p;
            if(t<move.windup)p=.4*t/move.windup;
            else if(t<move.windup+move.active)p=.4+.25*(t-move.windup)/move.active;
            else p=.65+.35*(t-move.windup-move.active)/move.recovery;
            return Math.min(n-1,(int)(p*n));
        }
        if(action==Action.CROUCH)return Math.min(n-1,(int)(age*24));
        if(action==Action.JUMP)return Math.min(n-1,(int)(age/.9*n));
        return (int)(age*(action==Action.WALK?12:8))%n;
    }
    void draw(Graphics2D g,FightEngine.Fighter fighter,int facing) {
        Action a=fighter==null?Action.IDLE:action;Clip clip=clips.get(a);Frame frame=clip.frames[fighter==null?0:frameIndex(fighter)];
        Graphics2D sprite=(Graphics2D)g.create();
        sprite.scale(facing*clip.scale,clip.scale);sprite.drawImage(frame.image,-frame.pivot,-frame.floor,null);sprite.dispose();
    }
    void portrait(Graphics2D g,int x,int y,int w,int h) {
        Frame frame=clips.get(Action.IDLE).frames[0];Rectangle b=frame.bounds;
        Graphics2D p=(Graphics2D)g.create();p.clipRect(x,y,w,h);
        p.setPaint(new GradientPaint(x,y,new Color(0x70522d),x+w,y+h,new Color(0x182132)));p.fillRect(x,y,w,h);
        double scale=Math.max(w/(double)b.width,h/(b.height*.65));
        p.translate(x+w/2.0,y);p.scale(scale,scale);p.drawImage(frame.image,-b.x-b.width/2,-b.y,null);p.dispose();
    }
}
