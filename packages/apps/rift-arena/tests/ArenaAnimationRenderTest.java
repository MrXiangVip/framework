package com.packages.riftarena;
import java.awt.*;
import java.awt.image.*;
import javax.imageio.ImageIO;
import java.io.*;

/** Actual OpenGL snapshots of the four imported skinned characters, not synthetic test geometry. */
public final class ArenaAnimationRenderTest {
    public static void main(String[] args)throws Exception{
        ArenaRenderer renderer=new ArenaRenderer();renderer.open(ArenaAnimationRenderTest.class.getClassLoader(),900,600);
        try{
            ArenaWorld bow=ArenaSwordTest.fresh();bow.configure(bow.player,1);ArenaSwordTest.dummy(bow).x=7;
            renderer.render(bow);ArenaSwordTest.begin(bow);
            BufferedImage bowSheet=new BufferedImage(1080,480,BufferedImage.TYPE_INT_RGB);Graphics2D bg=bowSheet.createGraphics();
            int bowTick=0;int[] bowTicks={8,18,24,30,38,48};
            for(int i=0;i<bowTicks.length;i++){
                while(bowTick<bowTicks[i]){bow.update(.01,0,0,false);bowTick++;renderer.render(bow);}
                BufferedImage shot=renderer.render(bow);
                bg.drawImage(shot.getSubimage(335,170,360,240),(i%3)*360,(i/3)*240,null);
                bg.setColor(Color.WHITE);bg.drawString(String.format("Bow %.2fs / target HP %.0f",bowTick*.01,ArenaSwordTest.dummy(bow).hp),(i%3)*360+10,(i/3)*240+20);
            }
            bg.dispose();ImageIO.write(bowSheet,"png",new File("out/rift-arena-tests/archer-attack.png"));
            ArenaWorld retreat=ArenaSwordTest.fresh();retreat.configure(retreat.player,1);ArenaSwordTest.dummy(retreat).x=7;
            retreat.aimX=7;retreat.aimZ=0;renderer.render(retreat);retreat.cast(0);
            BufferedImage retreatSheet=new BufferedImage(1080,480,BufferedImage.TYPE_INT_RGB);Graphics2D rg=retreatSheet.createGraphics();
            int retreatTick=0;int[] retreatTicks={6,17,27,39,53,88};
            for(int i=0;i<retreatTicks.length;i++){
                while(retreatTick<retreatTicks[i]){retreat.update(.01,0,0,false);retreatTick++;renderer.render(retreat);}
                BufferedImage shot=renderer.render(retreat);
                rg.drawImage(shot.getSubimage(335,170,360,240),(i%3)*360,(i/3)*240,null);
                rg.setColor(Color.WHITE);rg.drawString(String.format("Retreat %.2fs / HP %.0f",retreatTick*.01,ArenaSwordTest.dummy(retreat).hp),(i%3)*360+10,(i/3)*240+20);
            }
            rg.dispose();ImageIO.write(retreatSheet,"png",new File("out/rift-arena-tests/archer-retreat.png"));
            ArenaWorld scatter=ArenaSwordTest.fresh();scatter.configure(scatter.player,1);scatter.units.remove(1);
            for(int i=-1;i<=1;i++){double a=Math.toRadians(i*18);ArenaWorld.Unit target=scatter.spawn(ArenaWorld.Kind.SOLDIER,1,7*Math.cos(a),7*Math.sin(a));target.speed=target.range=target.attack=0;}
            scatter.aimX=12;scatter.aimZ=0;renderer.render(scatter);scatter.cast(1);
            BufferedImage scatterSheet=new BufferedImage(1080,480,BufferedImage.TYPE_INT_RGB);Graphics2D fg=scatterSheet.createGraphics();
            int scatterTick=0;int[] scatterTicks={8,19,27,34,43,65};
            for(int i=0;i<scatterTicks.length;i++){
                while(scatterTick<scatterTicks[i]){scatter.update(.01,0,0,false);scatterTick++;renderer.render(scatter);}
                BufferedImage shot=renderer.render(scatter);
                fg.drawImage(shot.getSubimage(335,170,360,240),(i%3)*360,(i/3)*240,null);
                fg.setColor(Color.WHITE);fg.drawString(String.format("Scatter %.2fs / arrows %d",scatterTick*.01,scatter.fanArrows.size()),(i%3)*360+10,(i/3)*240+20);
            }
            fg.dispose();ImageIO.write(scatterSheet,"png",new File("out/rift-arena-tests/archer-scatter.png"));
            BufferedImage structures=new BufferedImage(1800,1200,BufferedImage.TYPE_INT_RGB);
            Graphics2D buildings=structures.createGraphics();
            for(int state=0;state<4;state++){
                ArenaWorld preview=new ArenaWorld();preview.reset(0);preview.player.x=-30;preview.player.z=0;
                for(ArenaWorld.Unit u:preview.units)if(u.kind==ArenaWorld.Kind.CORE||u.kind==ArenaWorld.Kind.TOWER){
                    if(state==1){u.cooldown=1;u.hitFlash=.1;}
                    if(state==2)u.hp=u.maxHp*.3;
                    if(state==3){u.hp=0;u.deathAt=0;}
                }
                preview.elapsed=state==3?1.2:.6;
                buildings.drawImage(renderer.render(preview),(state%2)*900,(state/2)*600,null);
                buildings.setColor(Color.WHITE);buildings.drawString(new String[]{"Forest sanctuary / intact","Attack pulse / hit","Damaged stonework","Destroyed / rubble"}[state],(state%2)*900+16,(state/2)*600+24);
            }
            buildings.dispose();ImageIO.write(structures,"png",new File("out/rift-arena-tests/structures.png"));
            String[] actions={"idle","run","attack","hurt","death"};
            BufferedImage sheet=new BufferedImage(1100,760,BufferedImage.TYPE_INT_RGB);Graphics2D g=sheet.createGraphics();
            for(int hero=0;hero<4;hero++)for(int state=0;state<actions.length;state++){
                ArenaWorld world=new ArenaWorld();world.reset(Math.min(hero,2));world.units.clear();
                ArenaWorld.Unit unit=hero==3?world.spawn(ArenaWorld.Kind.SOLDIER,0,0,0):world.player;
                if(hero<3)world.units.add(unit);world.player.x=0;world.player.z=0;unit.x=0;unit.z=0;unit.facing=unit.attackFacing=0;
                if(state==1){unit.travel=1;unit.lastMovedAt=0;}
                if(state==2){unit.attackTime=ArenaWorld.attackDuration(unit);unit.attackSerial++;}
                if(state==3)unit.hitFlash=.14;
                if(state==4){unit.hp=0;unit.respawn=7;unit.deathAt=0;}
                renderer.render(world);
                world.elapsed=state==4?1.6:.24;
                if(state==1){unit.travel=2.6;unit.lastMovedAt=world.elapsed;}
                if(state==2)unit.attackTime=ArenaWorld.attackDuration(unit)*.70;
                if(state==3)unit.hitFlash=.07;
                BufferedImage image=renderer.render(world);
                g.drawImage(image.getSubimage(345,170,210,190),state*220,hero*190,220,190,null);
                g.setColor(Color.WHITE);g.drawString((hero==0?"warrior":hero==1?"archer":hero==2?"mage":"soldier")+" / "+actions[state],state*220+8,hero*190+18);
            }
            g.dispose();ImageIO.write(sheet,"png",new File("out/rift-arena-tests/skeletal-animation.png"));
            ArenaWorld sword=ArenaSwordTest.fresh();
            BufferedImage sequence=new BufferedImage(1080,420,BufferedImage.TYPE_INT_RGB);Graphics2D sg=sequence.createGraphics();
            renderer.render(sword);sword.requestAttack();sword.update(.01,0,0,false);
            int tick=0;int[] ticks={4,12,17,21,32,58};
            for(int i=0;i<ticks.length;i++){
                while(tick<ticks[i]){sword.update(.01,0,0,false);tick++;renderer.render(sword);}
                BufferedImage frame=renderer.render(sword);
                sg.drawImage(frame.getSubimage(330,170,270,210),(i%3)*360,(i/3)*210,360,210,null);
                sg.setColor(Color.WHITE);sg.drawString(String.format("Sword %.2fs / enemy HP %.0f",tick*.01,ArenaSwordTest.dummy(sword).hp),(i%3)*360+10,(i/3)*210+18);
            }
            sg.dispose();ImageIO.write(sequence,"png",new File("out/rift-arena-tests/sword-attack.png"));
            ArenaWorld combo=ArenaSwordTest.fresh();ArenaSwordTest.dummy(combo).hp=ArenaSwordTest.dummy(combo).maxHp=10000;
            BufferedImage comboSheet=new BufferedImage(1080,420,BufferedImage.TYPE_INT_RGB);Graphics2D comboGraphics=comboSheet.createGraphics();
            renderer.render(combo);combo.requestAttack();combo.update(.01,0,0,false);
            for(int segment=0;segment<2;segment++){
                int serial=combo.player.attackSerial;
                for(int i=0;i<3;i++){
                    double phase=new double[]{.20,.30,.36}[i];
                    while(combo.player.attackTime>ArenaWorld.SWORD_ATTACK_SECONDS*(1-phase)){combo.update(.005,0,0,false);renderer.render(combo);}
                    BufferedImage frame=renderer.render(combo);
                    comboGraphics.drawImage(frame.getSubimage(330,170,270,210),i*360,segment*210,360,210,null);
                    comboGraphics.setColor(Color.WHITE);comboGraphics.drawString("Attack "+(segment==0?"A":"B")+" / phase "+phase,i*360+10,segment*210+18);
                }
                if(segment==0)while(combo.player.attackSerial==serial){combo.update(.01,0,0,true);renderer.render(combo);}
            }
            comboGraphics.dispose();ImageIO.write(comboSheet,"png",new File("out/rift-arena-tests/sword-combo.png"));
            ArenaWorld guard=ArenaSwordTest.fresh();guard.units.remove(1);guard.player.combatTime=2;
            BufferedImage guardSheet=new BufferedImage(1080,210,BufferedImage.TYPE_INT_RGB);Graphics2D guardGraphics=guardSheet.createGraphics();
            renderer.render(guard);
            for(int i=0;i<4;i++){
                for(int j=0;j<12;j++){guard.update(.01,i==1||i==2?1:0,0,false);renderer.render(guard);}
                if(i==3){guard.player.combatTime=0;guard.update(.12,0,0,false);renderer.render(guard);guard.update(.12,0,0,false);}
                BufferedImage frame=renderer.render(guard);
                guardGraphics.drawImage(frame.getSubimage(330,170,270,210),i*270,0,270,210,null);
                guardGraphics.setColor(Color.WHITE);guardGraphics.drawString(new String[]{"Combat idle","Combat walk","Combat walk / turn","Ordinary idle"}[i],i*270+8,18);
            }
            guardGraphics.dispose();ImageIO.write(guardSheet,"png",new File("out/rift-arena-tests/combat-stance.png"));
            ArenaWorld meteor=ArenaSwordTest.fresh();ArenaSwordTest.dummy(meteor).hp=ArenaSwordTest.dummy(meteor).maxHp=2000;ArenaSwordTest.dummy(meteor).range=0;meteor.aimX=3;
            BufferedImage meteorSheet=new BufferedImage(1170,520,BufferedImage.TYPE_INT_RGB);Graphics2D mg=meteorSheet.createGraphics();
            renderer.render(meteor);meteor.cast(2);int mt=0;int[] meteorTicks={12,30,55,82,93,115};
            for(int i=0;i<meteorTicks.length;i++){
                while(mt<meteorTicks[i]){meteor.update(.01,0,0,false);mt++;renderer.render(meteor);}
                BufferedImage frame=renderer.render(meteor);mg.drawImage(frame.getSubimage(300,60,390,360),(i%3)*390,(i/3)*260,390,260,null);
                mg.setColor(Color.WHITE);mg.drawString(String.format("R %.2fs / enemy HP %.0f",mt*.01,ArenaSwordTest.dummy(meteor).hp),(i%3)*390+10,(i/3)*260+18);
            }
            mg.dispose();ImageIO.write(meteorSheet,"png",new File("out/rift-arena-tests/sword-meteor.png"));
            ArenaWorld hurt=ArenaSwordTest.fresh();ArenaWorld.Unit victim=ArenaSwordTest.dummy(hurt);victim.range=0;
            renderer.render(hurt);for(int warm=0;warm<20;warm++){hurt.update(.01,0,0,false);renderer.render(hurt);}hurt.damage(victim,62,0,hurt.player);
            BufferedImage feedback=new BufferedImage(1080,210,BufferedImage.TYPE_INT_RGB);Graphics2D hg=feedback.createGraphics();
            int ht=0;int[] frames={1,8,18,34};
            for(int i=0;i<frames.length;i++){
                while(ht<frames[i]){hurt.update(.01,0,0,false);ht++;renderer.render(hurt);}
                BufferedImage frame=renderer.render(hurt);
                hg.drawImage(frame.getSubimage(370,180,180,160),i*270,0,270,210,null);hg.setColor(Color.WHITE);hg.drawString(String.format("Hit reaction %.2fs",ht*.01),i*270+8,18);
            }
            hg.dispose();ImageIO.write(feedback,"png",new File("out/rift-arena-tests/hit-feedback.png"));
            ArenaWorld slam=ArenaSwordTest.fresh();slam.player.x=-12;ArenaWorld.Unit dummy=ArenaSwordTest.dummy(slam);dummy.x=-9;dummy.range=0;
            slam.aimX=-8;slam.aimZ=0;renderer.render(slam);
            for(int warm=0;warm<40;warm++){slam.update(.01,0,0,false);renderer.render(slam);}
            slam.cast(1);
            BufferedImage slams=new BufferedImage(1170,520,BufferedImage.TYPE_INT_RGB);Graphics2D cg=slams.createGraphics();
            int st=0;int[] shots={4,16,24,34,48,85};
            for(int i=0;i<shots.length;i++){
                while(st<shots[i]){slam.update(.01,0,0,false);st++;renderer.render(slam);}
                BufferedImage frame=renderer.render(slam);
                cg.drawImage(frame.getSubimage(300,140,320,240),(i%3)*390,(i/3)*260,390,260,null);
                cg.setColor(Color.WHITE);cg.drawString(String.format("E slam %.2fs / enemy HP %.0f",st*.01,dummy.hp),(i%3)*390+10,(i/3)*260+18);
            }
            cg.dispose();ImageIO.write(slams,"png",new File("out/rift-arena-tests/sword-slam.png"));
            ArenaWorld dash=ArenaSwordTest.fresh();dash.player.x=-15;ArenaWorld.Unit dashTarget=ArenaSwordTest.dummy(dash);dashTarget.x=-8.2;dashTarget.range=0;dash.aimX=-5;dash.aimZ=0;
            renderer.render(dash);for(int warm=0;warm<20;warm++){dash.update(.01,0,0,false);renderer.render(dash);}dash.cast(0);
            BufferedImage dashes=new BufferedImage(1170,520,BufferedImage.TYPE_INT_RGB);Graphics2D dg=dashes.createGraphics();int tickDash=0;int[] moments={0,4,9,15,20,28};
            for(int i=0;i<moments.length;i++){
                while(tickDash<moments[i]){dash.update(.01,0,0,false);tickDash++;renderer.render(dash);}
                BufferedImage frame=renderer.render(dash);dg.drawImage(frame.getSubimage(280,150,340,240),(i%3)*390,(i/3)*260,390,260,null);
                dg.setColor(Color.WHITE);dg.drawString(String.format("Q %.2fs / distance %.2f / HP %.0f",tickDash*.01,dash.player.x+15,dashTarget.hp),(i%3)*390+8,(i/3)*260+18);
            }
            dg.dispose();ImageIO.write(dashes,"png",new File("out/rift-arena-tests/sword-dash.png"));
            ArenaWorld busy=ArenaSwordTest.fresh();
            for(int i=0;i<32;i++){ArenaWorld.Unit minion=busy.spawn(ArenaWorld.Kind.SOLDIER,i%2,(i%8-4)*2,(i/8-2)*2);minion.speed=minion.attack=minion.range=0;}
            long[] samples=new long[40];
            for(int i=-10;i<samples.length;i++){
                busy.update(.016,0,0,false);long begin=System.nanoTime();renderer.render(busy);if(i>=0)samples[i]=System.nanoTime()-begin;
            }
            java.util.Arrays.sort(samples);
            System.out.printf(java.util.Locale.ROOT,"Render timing: 34 characters, median %.2fms / p95 %.2fms (test machine, GL + readback)%n",samples[20]/1e6,samples[38]/1e6);
            System.out.println("PASS: actual OpenGL skinned animation states rendered without GL errors");
        }finally{renderer.close();}
    }
}
