package com.pacakges.dwj;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

/** Original vector scenery, shared by live previews and the combat arena. */
public final class StageArt {
    public static final String[] NAMES = {"霓虹天台", "落日港湾", "月下道场", "钢铁工厂", "雪山之巅", "午夜机场"};
    public static final String[] CODES = {"NEON ROOFTOP", "SUNSET HARBOR", "MOONLIT DOJO", "STEEL WORKS", "FROZEN SUMMIT", "MIDNIGHT AIRPORT"};
    private static final int[] TOP = {0x101226,0x332644,0x101d31,0x181c25,0x172b47,0x091c33};
    private static final int[] HORIZON = {0x583055,0xf59965,0x486a80,0x795143,0x8bb9ce,0x37597b};
    private static final int[] ACCENT = {0x65e7ea,0xffce89,0xbbe5df,0xffae58,0xe0f7ff,0x7cc9ff};
    private final BufferedImage[] scenes = new BufferedImage[NAMES.length];
    public int accent(int id) { return ACCENT[Math.floorMod(id,NAMES.length)]; }
    public BufferedImage image(int id) {
        id=Math.floorMod(id,NAMES.length);
        if(scenes[id]==null) scenes[id]=create(id);
        return scenes[id];
    }
    public void draw(Graphics2D g,int id,int x,int y,int w,int h) { g.drawImage(image(id),x,y,w,h,null); }
    private BufferedImage create(int id) {
        BufferedImage image=new BufferedImage(1280,720,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0,0,new Color(TOP[id]),0,540,new Color(HORIZON[id])));g.fillRect(0,0,1280,720);
        Random r=new Random(837+id);
        if(id!=1) for(int i=0;i<100;i++) {g.setColor(new Color(235,246,255,40+r.nextInt(100)));g.fillOval(r.nextInt(1280),r.nextInt(320),2,2);}
        g.setColor(new Color(ACCENT[id]));g.fillOval(id==1?870:940,id==1?170:90,id==1?170:78,id==1?170:78);
        if(id==0 || id==5) {
            for(int layer=0;layer<2;layer++) for(int x=-30;x<1280;) {
                int w=45+r.nextInt(70),h=80+r.nextInt(layer==0?160:240),y=510-h;
                g.setColor(new Color(layer==0?0x222c43:0x111d30));g.fillRect(x,y,w,h);
                for(int wy=y+12;wy<510;wy+=18) for(int wx=x+9;wx<x+w-6;wx+=14) if(r.nextInt(4)!=0) {
                    g.setColor(new Color(id==0?0x76b6c5:0x8da8bb));g.fillRect(wx,wy,3,6);
                }
                if(id==0&&layer==1) {g.setColor(new Color(x%2==0?0x6df0ed:0xe36e95));g.fillRect(x+4,y+12,4,45);}
                x+=w+12;
            }
            if(id==0) {
                g.setColor(new Color(0x182334));g.fillRect(78,370,120,140);g.fillRect(1050,305,150,205);
                g.setColor(new Color(0x70e5e3));g.drawRect(93,386,89,51);g.setFont(new Font("SansSerif",Font.BOLD,22));g.drawString("ARCADE",96,419);
                g.setColor(new Color(0xe788a2));g.drawRect(1068,322,113,62);g.drawString("K · O · F",1078,360);
            } else {
                g.setColor(new Color(0x111e31));g.fillRect(0,440,450,73);g.fillRect(70,320,30,130);g.fillRect(42,310,90,25);
                g.setColor(new Color(0xa4def3));g.fillRect(48,317,77,7);
                g.setColor(new Color(0xb1becb));g.fillPolygon(new int[]{620,790,850,1020,950,815,750},new int[]{439,431,385,422,441,441,469},7);
            }
        } else if(id==1) {
            g.setPaint(new GradientPaint(0,360,new Color(0x997778),0,550,new Color(0x303e52)));g.fillRect(0,365,1280,190);
            for(int i=0;i<36;i++){g.setColor(new Color(255,205,149,35));g.fillRect(760+r.nextInt(340),375+i*5,30+r.nextInt(110),2);}
            g.setColor(new Color(0x303346));g.fillPolygon(new int[]{40,380,430,100},new int[]{419,419,465,465},4);g.fillRect(160,375,130,45);
            for(int x:new int[]{60,1120}) {g.setStroke(new BasicStroke(9));g.drawLine(x,130,x,520);g.drawLine(x,130,x+180,185);g.drawLine(x+160,180,x+160,370);}
            g.setColor(new Color(0x343848));g.fillRect(1010,430,200,75);g.setColor(new Color(0x65606a));g.fillRect(1040,397,130,33);
        } else if(id==2 || id==4) {
            for(int layer=0;layer<3;layer++) {
                g.setColor(new Color(id==4?new int[]{0x688b9f,0x47697f,0x28485e}[layer]:new int[]{0x355261,0x27414e,0x192e3c}[layer]));
                for(int x=-180;x<1400;x+=260) {
                    int peak=190+r.nextInt(170)+layer*45;
                    g.fillPolygon(new int[]{x,x+180,x+380},new int[]{550,peak,550},3);
                    if(id==4) {g.setColor(new Color(0xc3e0e6));g.fillPolygon(new int[]{x+135,x+180,x+223,x+190,x+167},new int[]{peak+88,peak,peak+84,peak+56,peak+67},5);g.setColor(new Color(new int[]{0x688b9f,0x47697f,0x28485e}[layer]));}
                }
            }
            if(id==2) {
                g.setColor(new Color(0x172331));g.fillRect(96,260,22,285);g.fillRect(1140,260,22,285);g.fillRect(70,275,1120,24);
                g.fillPolygon(new int[]{45,100,640,1180,1235,1180,640,100},new int[]{270,248,215,248,270,264,238,264},8);
                for(int x:new int[]{150,1100}) {g.setColor(new Color(0xe1b277));g.fillRoundRect(x,330,32,52,12,12);g.setColor(new Color(0x452f33));g.fillRect(x+12,299,6,32);}
            } else for(int i=0;i<90;i++){g.setColor(new Color(232,248,255,120));g.fillOval(r.nextInt(1280),r.nextInt(550),3,3);}
        } else {
            for(int x=0;x<1280;x+=170){g.setColor(new Color(0x252b31));g.fillRect(x,190+r.nextInt(100),120,340);g.fillRect(x+35,80,32,260);g.setColor(new Color(0x9b6450));g.fillRect(x+15,340,80,7);}
            g.setStroke(new BasicStroke(12));g.setColor(new Color(0x111d29));g.drawLine(0,100,1280,100);
            for(int x=80;x<1280;x+=240){g.drawLine(x,0,x,510);g.setColor(new Color(0xffbc77));g.fillOval(x-4,155,8,8);g.setColor(new Color(0x111d29));}
        }
        int floor=id==4?0x718d9c:id==2?0x35414a:0x263142;
        g.setPaint(new GradientPaint(0,520,new Color(floor),0,720,new Color(0x0b1320)));g.fillRect(0,520,1280,200);
        g.setColor(new Color(ACCENT[id]));g.fillRect(0,519,1280,2);
        g.setColor(new Color(200,220,240,24));g.setStroke(new BasicStroke(1));
        for(int x=-800;x<2100;x+=160) g.drawLine(640+(x-640)/3,521,x,720);
        for(int y:new int[]{543,578,633,710})g.drawLine(0,y,1280,y);
        if(id==0||id==5){g.setColor(new Color(0x101d2b));g.fillRect(0,488,1280,8);for(int x=20;x<1280;x+=80)g.fillRect(x,460,4,60);}
        g.dispose();return image;
    }
}
