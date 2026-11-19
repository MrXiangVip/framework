package com.packages.riftarena;
import java.awt.*;
import java.awt.image.*;
import javax.imageio.ImageIO;
import java.io.File;

/** Compare cached color AND depth with actual uncached draws at camera bounds and occluders. */
public final class ArenaSceneCacheTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        ArenaRenderer r=new ArenaRenderer();r.open(ArenaSceneCacheTest.class.getClassLoader(),900,600);
        try{
            double[][] positions={{-33,-18},{0,0},{33,18},{-10,-14},{12,14},{.033,.041},{-16.95,-4.96}};
            BufferedImage sheet=new BufferedImage(1800,positions.length*300,BufferedImage.TYPE_INT_RGB);Graphics2D g=sheet.createGraphics();
            for(int n=0;n<positions.length;n++){
                ArenaWorld w=new ArenaWorld();w.reset(0);w.waveTimer=1000;
                w.player.x=positions[n][0];w.player.z=positions[n][1];w.elapsed=1;
                w.player.attackTime=.46;w.player.attackSerial++;w.player.attackFacing=90;
                w.effects.add(new ArenaWorld.Effect(w.player.x,w.player.z,6,0,3,.3));
                r.culling=false;r.cachedScene=false;BufferedImage reference=r.render(w);
                r.cachedScene=true;BufferedImage cached=r.render(w);
                if(n==1||n==4)ImageIO.write(cached,"png",new File("out/rift-arena-tests/landscape-"+(n==1?"river":"road")+".png"));
                int[] a=((DataBufferInt)reference.getRaster().getDataBuffer()).getData(),b=((DataBufferInt)cached.getRaster().getDataBuffer()).getData();
                int different=0;long error=0;
                for(int i=0;i<a.length;i++){
                    int worst=0;for(int shift:new int[]{0,8,16}){int diff=Math.abs((a[i]>>shift&255)-(b[i]>>shift&255));worst=Math.max(worst,diff);error+=diff;}
                    if(worst>12)different++;
                }
                double fraction=different/(double)a.length,mean=error/(double)(a.length*3);
                System.out.printf(java.util.Locale.ROOT,"Cache parity %d: >12 RGB error %.4f%% / mean %.4f%n",n,fraction*100,mean);
                check(fraction<.005&&mean<.6,"Cached color/depth must match uncached scene at camera "+n);
                r.culling=true;BufferedImage culled=r.render(w);int changed=0;
                int[] c=((DataBufferInt)culled.getRaster().getDataBuffer()).getData();
                for(int i=0;i<b.length;i++)if(b[i]!=c[i])changed++;
                check(changed==0,"Culling must preserve visible pixels");
                g.drawImage(reference,0,n*300,900,300,null);g.drawImage(culled,900,n*300,900,300,null);
                g.setColor(Color.WHITE);g.drawString("Uncached / camera "+n,10,n*300+18);g.drawString("Cached + culled",910,n*300+18);
            }
            g.dispose();ImageIO.write(sheet,"png",new File("out/rift-arena-tests/scene-cache-parity.png"));
            for(double zoom:new double[]{.85,1.25}){
                ArenaWorld zoomed=new ArenaWorld();zoomed.reset(2);zoomed.cameraZoom=zoom;zoomed.player.x=16;zoomed.player.z=4;zoomed.elapsed=1;
                r.culling=false;r.cachedScene=false;BufferedImage reference=r.render(zoomed);r.cachedScene=true;BufferedImage cached=r.render(zoomed);
                int[] a=((DataBufferInt)reference.getRaster().getDataBuffer()).getData(),b=((DataBufferInt)cached.getRaster().getDataBuffer()).getData();int differences=0;
                for(int i=0;i<a.length;i++){int worst=0;for(int shift=0;shift<=16;shift+=8)worst=Math.max(worst,Math.abs((a[i]>>shift&255)-(b[i]>>shift&255)));if(worst>12)differences++;}
                check(differences/(double)a.length<.005,"Zoom cache color/depth parity "+zoom);
                r.culling=true;BufferedImage culled=r.render(zoomed);int[] c=((DataBufferInt)culled.getRaster().getDataBuffer()).getData();for(int i=0;i<b.length;i++)check(b[i]==c[i],"Zoom culling preserves pixels");
                ImageIO.write(cached,"png",new File("out/rift-arena-tests/zoom-"+zoom+".png"));
            }
            ArenaWorld cover=new ArenaWorld();cover.reset(0);cover.units.clear();cover.units.add(cover.player);cover.elapsed=10;
            ArenaRenderer.Canopy chosen=null;for(ArenaRenderer.Canopy tree:r.canopies)if(Math.abs(tree.x)<17&&tree.z<0&&tree.z>-16){chosen=tree;break;}
            check(chosen!=null,"Forest occluder available");cover.player.x=chosen.x;cover.player.z=chosen.z-chosen.height*.62*ArenaRenderer.COS/ArenaRenderer.SIN+2*ArenaRenderer.COS/ArenaRenderer.SIN;
            for(ArenaRenderer.Canopy tree:r.canopies)tree.alpha=1;r.canopyTime=cover.elapsed;
            BufferedImage opaqueTrees=r.render(cover);cover.elapsed+=.3;
            r.cachedScene=false;BufferedImage fadedReference=r.render(cover);check(r.fadedTrees>0&&chosen.alpha<.3,"Blocking tree fades to reveal player");
            r.cachedScene=true;BufferedImage fadedCached=r.render(cover);int patchErrors=0;long patchError=0;
            for(int y=0;y<600;y++)for(int x=0;x<900;x++){
                int a=fadedReference.getRGB(x,y),b=fadedCached.getRGB(x,y),worst=0;
                for(int shift:new int[]{0,8,16}){int diff=Math.abs((a>>shift&255)-(b>>shift&255));worst=Math.max(worst,diff);patchError+=diff;}
                if(worst>12)patchErrors++;
            }
            check(patchErrors<2700&&patchError/(900.*600*3)<.6,"Tree patch restoration matches full terrain/depth rendering");
            BufferedImage coverSheet=new BufferedImage(1800,600,BufferedImage.TYPE_INT_RGB);Graphics2D cg=coverSheet.createGraphics();cg.drawImage(opaqueTrees,0,0,null);cg.drawImage(fadedCached,900,0,null);
            cg.setColor(Color.WHITE);cg.drawString("Opaque canopy / player behind tree",10,18);cg.drawString("Local fade / player stays visible",910,18);cg.dispose();ImageIO.write(coverSheet,"png",new File("out/rift-arena-tests/camera-occlusion.png"));
            double alpha=chosen.alpha;cover.pause();cover.update(.2,0,0,false);r.render(cover);check(chosen.alpha==alpha,"Pause freezes canopy fade");
            cover.resume();cover.player.x=cover.player.z=0;cover.elapsed+=.3;r.render(cover);check(r.fadedTrees==0,"Trees restore after player leaves projected obstruction");
            ArenaWorld river=new ArenaWorld();river.reset(0);river.units.clear();river.player.x=river.player.z=0;river.elapsed=1;
            BufferedImage waterA=r.render(river);river.elapsed=1.4;BufferedImage waterB=r.render(river);
            int motion=0;for(int y=90;y<180;y++)for(int x=430;x<470;x++)if(waterA.getRGB(x,y)!=waterB.getRGB(x,y))motion++;
            check(motion>10,"Water ripples visibly move inside cached river surface");
            river.pause();river.update(.2,0,0,false);BufferedImage waterPaused=r.render(river);
            for(int y=90;y<180;y++)for(int x=430;x<470;x++)check(waterB.getRGB(x,y)==waterPaused.getRGB(x,y),"Paused water preserves its exact animation phase");
            BufferedImage motionSheet=new BufferedImage(900,600,BufferedImage.TYPE_INT_RGB);Graphics2D wg=motionSheet.createGraphics();
            BufferedImage[] waterFrames={waterA,waterB,waterPaused};
            for(int i=0;i<3;i++){wg.drawImage(waterFrames[i].getSubimage(300,0,300,600),i*300,0,null);wg.setColor(Color.WHITE);wg.drawString(new String[]{"Water / 1.0s","Water / 1.4s","Paused / same phase"}[i],i*300+8,18);}
            wg.dispose();ImageIO.write(motionSheet,"png",new File("out/rift-arena-tests/water-motion.png"));
            ArenaWorld w=ArenaSwordTest.fresh();w.player.x=-33;ArenaWorld.Unit far=ArenaSwordTest.dummy(w);far.x=40;far.speed=3;
            w.update(.1,0,0,false);r.render(w);
            check(r.drawnUnits==1&&r.culledUnits==1&&far.x<40,"Offscreen simulation advances without drawing or skinning");
            check(r.visible(r.cameraX+(ArenaRenderer.VIEW_HEIGHT/2*1.5+5)-.01,r.cameraZ,5,8),"Conservative character edge bounds");
            check(!r.visible(r.cameraX+(ArenaRenderer.VIEW_HEIGHT/2*1.5+5)+.01,r.cameraZ,5,8),"Fully offscreen character culled");
            System.out.println("PASS: cached color/depth parity, camera bounds, foreground occlusion, combat effects, pixel-aligned scrolling and conservative culling");
        }finally{r.close();}
    }
}
