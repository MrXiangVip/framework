package com.packages.windward;

import com.android.view.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class MountainTest {
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    static void near(double a,double b,double tolerance,String reason){check(Math.abs(a-b)<tolerance,reason+": "+a+" / "+b);}
    static void advance(MountainWorld w,int n,double x,double z,boolean sprint,double yaw){for(int i=0;i<n;i++)w.step(x,z,sprint,yaw);}
    static void cullingCheck(MountainRenderer renderer)throws Exception{
        MountainWorld w=new MountainWorld();MountainCamera c=new MountainCamera();int views=0;
        for(int scene=0;scene<PerformanceTest.SCENES.length;scene++)for(int angle=0;angle<8;angle++){
            PerformanceTest.setup(w,c,scene);c.yaw=angle*Math.PI/4;c.pitch=angle%2==0?.12:.8;
            renderer.culling=false;BufferedImage reference=renderer.render(w,c);
            renderer.culling=true;BufferedImage actual=renderer.render(w,c);
            int[] a=((java.awt.image.DataBufferInt)reference.getRaster().getDataBuffer()).getData(),b=((java.awt.image.DataBufferInt)actual.getRaster().getDataBuffer()).getData();
            int changed=0;for(int i=0;i<a.length;i++)if(a[i]!=b[i])changed++;
            if(changed!=0){ImageIO.write(reference,"png",new File("out/windward-tests/cull-reference.png"));ImageIO.write(actual,"png",new File("out/windward-tests/cull-actual.png"));}
            check(changed==0,"Culling preserves all pixels: scene "+scene+" yaw "+angle+", changed="+changed);views++;
        }
        check(renderer.sceneDraws<renderer.sceneSize(),"View culling actually omits static draws");
        System.out.println("PASS: conservative culling matches uncropped render pixel-for-pixel across "+views+" scene/camera views");
    }
    static void logic(){
        MountainWorld fw=new MountainWorld();fw.x=fw.z=fw.y=0;MountainCamera fc=new MountainCamera();fc.eyeX=0;fc.eyeY=1.35;fc.eyeZ=10;
        MountainFrustum frustum=new MountainFrustum();frustum.update(fw,fc);
        check(frustum.visible(0,1001.35,0,1),"Visible sphere kept");check(!frustum.visible(0,1001.35,12,.1),"Behind camera culled");
        check(!frustum.visible(100,1001.35,0,1)&&!frustum.visible(0,1001.35,-220,1),"Side and far spheres culled");
        check(frustum.visible(10,1001.35,0,1)&&frustum.visible(0,1008.35,0,1),"Partially visible edge spheres retained");
        check(frustum.visible(0,1001.35,12,20),"Camera-enclosing bounds retained");
        MountainWorld w=new MountainWorld();
        double z=w.z;advance(w,120,0,1,false,0);near(z-w.z,4.5,.001,"Walking speed");
        near(w.y,MountainWorld.ground(w.x,w.z),1e-8,"Slope contact");
        w.reset();double x=w.x;z=w.z;advance(w,120,1,1,false,0);near(Math.hypot(w.x-x,w.z-z),4.5,.001,"No diagonal speed boost");
        w.reset();z=w.z;advance(w,120,0,1,true,0);near(z-w.z,7,.001,"Sprint speed");
        w.reset();x=w.x;advance(w,120,0,1,false,Math.PI/2);near(w.x-x,4.5,.001,"Camera-relative movement");
        w.reset();double floor=w.y;w.jump();advance(w,20,0,0,false,0);check(w.y>floor+.5&&!w.grounded,"Jump rises");
        double velocity=w.vy;w.jump();near(w.vy,velocity,1e-8,"Wing deploy does not add jump impulse");check(w.gliding,"Airborne press deploys wing");w.jump();check(!w.gliding,"Next press retracts wing");
        advance(w,180,0,0,false,0);near(w.y,floor,1e-8,"Jump lands exactly");check(w.grounded,"Ground state restored");
        w.reset();w.paused=true;w.jump();advance(w,120,1,1,true,0);near(w.z,12,1e-8,"Pause stops movement");near(w.time,0,1e-8,"Pause stops time");check(w.grounded,"Pause blocks jump");
        w.reset();w.x=47.6;w.y=MountainWorld.ground(w.x,w.z);advance(w,240,1,0,true,0);check(w.x>55,"Original map edge is traversable");
        w.reset();MountainWorld.Obstacle rock=w.rocks.get(0);w.x=rock.x;w.z=rock.z+rock.r+MountainWorld.RADIUS+.1;w.y=MountainWorld.ground(w.x,w.z);
        advance(w,240,0,1,true,0);check(w.z>=rock.z+rock.r+MountainWorld.RADIUS-.001,"Rock blocks traversal");
        MountainWorld.Obstacle tree=w.trees.get(0);w.x=tree.x;w.z=tree.z+tree.r+MountainWorld.RADIUS+.1;w.y=MountainWorld.ground(w.x,w.z);
        advance(w,120,0,1,true,0);check(w.z>=tree.z+tree.r+MountainWorld.RADIUS-.001,"Tree trunk blocks traversal");
        for(MountainWorld.Obstacle prop:w.props){
            w.reset();w.x=prop.x;w.z=prop.z+prop.r+MountainWorld.RADIUS+.1;w.y=MountainWorld.ground(w.x,w.z);
            advance(w,120,0,1,true,0);check(w.z>=prop.z+prop.r+MountainWorld.RADIUS-.001,"Imported prop blocks traversal");
        }
        w.reset();w.step(0,1,false,0);check(w.locomotion>0&&w.locomotion<.2,"Start blends rather than snaps");
        advance(w,90,0,1,true,0);check(w.runBlend>.95,"Sprint blend settles");
        double phase=w.gait;advance(w,120,0,0,false,0);check(w.locomotion<.001,"Stop settles into idle");near(w.gait,phase,1e-8,"No gait sliding at rest");
        w.jump();double maxLanding=0;for(int i=0;i<150;i++){w.step(0,0,false,0);maxLanding=Math.max(maxLanding,w.landing);}check(maxLanding>.5,"Landing absorbs jump impact");
        w.reset();w.visualFacing=Math.PI-.01;w.facing=-Math.PI+.01;w.step(0,0,false,0);check(w.visualFacing>Math.PI-.01,"Turning follows shortest arc");
        double frozen=w.visualFacing;w.paused=true;w.step(1,0,true,0);near(w.visualFacing,frozen,1e-8,"Pause freezes animation");
        w.reset();
        for(int point=1;point<MountainJourney.ROUTE.length;point++){
            double tx=MountainJourney.ROUTE[point][0],tz=MountainJourney.ROUTE[point][1];
            for(int i=0;i<2400&&Math.hypot(w.x-tx,w.z-tz)>.3;i++){
                double dx=tx-w.x,dz=tz-w.z,len=Math.hypot(dx,dz);w.step(dx/len,-dz/len,false,0);
            }
            check(Math.hypot(w.x-tx,w.z-tz)<.3,"Route checkpoint reachable on foot: "+point);
        }
        check(w.journey.arrived(),"Ordered exploration reaches tower");
        w.reset();
        for(double pz=11.5;pz>=-25;pz-=.5){
            double px=MountainWorld.pathX(pz);
            for(int i=0;i<600&&Math.hypot(w.x-px,w.z-pz)>.12;i++){
                double dx=px-w.x,dz=pz-w.z,len=Math.hypot(dx,dz);w.step(dx/len,-dz/len,false,0);
            }
            check(Math.hypot(w.x-px,w.z-pz)<.12,"Painted trail walkable at "+pz);
        }
        check(w.journey.arrived(),"Following painted trail discovers tower");
        w.reset();w.x=15;w.z=-25;w.y=MountainWorld.ground(w.x,w.z);w.step(0,0,false,0);check(w.journey.stage==1,"Skipping route does not complete ordered discoveries");
        w.x=w.rune.x+2;w.z=w.rune.z;w.y=MountainWorld.ground(w.x,w.z);w.paused=true;w.journey.interact(w);check(!w.journey.runeRead,"Paused interaction ignored");
        w.paused=false;w.journey.interact(w);check(w.journey.runeRead,"Nearby rune can be read");
        double until=w.journey.noticeUntil;w.paused=true;advance(w,120,0,0,false,0);near(w.journey.noticeUntil,until,1e-8,"Pause preserves notice timer");
        w.reset();check(!w.journey.runeRead&&w.journey.stage==1&&w.journey.notice.isEmpty(),"Restart resets exploration");
        w.journey.interact(w);check(!w.journey.runeRead,"Distant rune cannot be read");
        MountainCamera camera=new MountainCamera();w.reset();camera.update(w);near(Math.sqrt(Math.pow(camera.eyeX-w.x,2)+Math.pow(camera.eyeY-w.y-1.35,2)+Math.pow(camera.eyeZ-w.z,2)),9,.001,"Orbit distance");
        w.x=rock.x;w.z=rock.z-rock.r-1;w.y=MountainWorld.ground(w.x,w.z);camera.pitch=.12;camera.update(w);check(camera.eyeZ<rock.z-rock.r,"Camera retracts before solid");
        double retracted=Math.sqrt(Math.pow(camera.eyeX-w.x,2)+Math.pow(camera.eyeY-w.y-1.35,2)+Math.pow(camera.eyeZ-w.z,2));
        w.reset();camera.update(w,1./60);
        double recovered=Math.sqrt(Math.pow(camera.eyeX-w.x,2)+Math.pow(camera.eyeY-w.y-1.35,2)+Math.pow(camera.eyeZ-w.z,2));
        check(recovered>retracted&&recovered<9,"Camera recovers gradually after obstruction");
        double held=camera.eyeZ;camera.update(w,0);near(camera.eyeZ,held,1e-9,"Paused camera recovery freezes");
        MountainCamera slow=new MountainCamera(),fast=new MountainCamera();
        slow.update(w,0);fast.update(w,0);slow.distance=fast.distance=15;
        for(int i=0;i<30;i++)slow.update(w,1./30);
        for(int i=0;i<120;i++)fast.update(w,1./120);
        near(slow.eyeZ,fast.eyeZ,1e-8,"Recovery independent of frame rate");
        camera.reset();camera.update(w,0);near(camera.eyeZ-w.z,9*Math.cos(camera.pitch),1e-8,"Reset clears camera damping");
        camera.orbit(1e5,1e5);near(camera.pitch,1.1,.001,"Pitch clamp");camera.zoom(1000);near(camera.distance,3,.001,"Zoom clamp");
        for(int ix=-45;ix<=45;ix+=3)for(int iz=-45;iz<=45;iz+=3){w.x=ix;w.z=iz;w.y=MountainWorld.ground(ix,iz);camera.yaw=ix*.1;camera.pitch=.12;camera.update(w);check(camera.eyeY>=MountainWorld.ground(camera.eyeX,camera.eyeZ)+.2,"Camera stays above terrain");}
        MountainInput input=new MountainInput();check(input.press('W')&&!input.press('w'),"Auto-repeat filtered and case normalized");check(input.state()==1,"Forward state");input.release('W');check(input.state()==0,"Release stops input");input.press(KeyEvent.RIGHT);input.clear();check(input.state()==0,"Focus reset clears input");
        w.reset();near(w.z,12,1e-8,"Restart returns to camp");check(!w.paused&&w.grounded,"Restart clears transient states");
        System.out.println("PASS: movement, slope contact, sprint, jump/landing, solids, bounds, pause/reset, camera clearance, input repeat");
    }
    public static void main(String[] args)throws Exception{
        logic();if(args.length==0)return;
        MountainWorld world=new MountainWorld();MountainCamera camera=new MountainCamera();
        new File("out/windward-tests").mkdirs();
        try(MountainRenderer renderer=new MountainRenderer()){
            renderer.open(world);cullingCheck(renderer);long start=System.nanoTime();
            BufferedImage image=renderer.render(world,camera);ImageIO.write(image,"png",new File("out/windward-tests/camp.png"));
            int sky=image.getRGB(450,15),ground=image.getRGB(450,550);check(sky!=ground,"Actual GL image contains terrain and sky");
            world.x=9;world.z=-17;world.y=MountainWorld.ground(world.x,world.z);camera.yaw=-.5;
            ImageIO.write(renderer.render(world,camera),"png",new File("out/windward-tests/hillside.png"));
            world.reset();world.facing=world.visualFacing=0;camera.reset();camera.distance=3.3;camera.pitch=.15;
            ImageIO.write(renderer.render(world,camera),"png",new File("out/windward-tests/traveller-front.png"));
            world.moving=true;world.gait=Math.PI/2;world.locomotion=1;
            ImageIO.write(renderer.render(world,camera),"png",new File("out/windward-tests/traveller-walk.png"));
            world.jump();advance(world,20,0,0,false,0);
            ImageIO.write(renderer.render(world,camera),"png",new File("out/windward-tests/traveller-jump.png"));
            check(org.lwjgl.opengl.GL11.glGetError()==0,"No OpenGL errors during model/material rendering");
            world.reset();world.x=4;world.z=-21;world.y=MountainWorld.ground(world.x,world.z);camera.reset();camera.distance=3.3;camera.pitch=.5;
            ImageIO.write(renderer.render(world,camera),"png",new File("out/windward-tests/tower-approach.png"));
            world.reset();world.x=-7;world.z=MountainTraversal.FRONT+MountainWorld.RADIUS+.04;world.y=world.floor();world.interact();advance(world,150,0,1,false,0);camera.reset();camera.distance=5;camera.yaw=.45;
            ImageIO.write(renderer.render(world,camera),"png",new File("out/windward-tests/climbing.png"));
            while(world.climbing)world.step(0,1,false,0);
            world.jump();advance(world,20,-1,0,false,0);world.jump();advance(world,55,-1,1,false,0);camera.reset();camera.distance=7;camera.yaw=-.7;
            ImageIO.write(renderer.render(world,camera),"png",new File("out/windward-tests/gliding.png"));
            world.reset();world.x=MountainCombat.HOME_X;world.z=MountainCombat.HOME_Z+1.6;world.y=world.floor();world.facing=world.visualFacing=Math.PI;advance(world,35,0,0,false,0);camera.reset();camera.distance=5.5;camera.yaw=.6;
            ImageIO.write(renderer.render(world,camera),"png",new File("out/windward-tests/guard-windup.png"));
            world.combat.attack(world);advance(world,20,0,0,false,0);
            ImageIO.write(renderer.render(world,camera),"png",new File("out/windward-tests/melee.png"));
            world.reset();world.x=22;world.z=MountainPuzzle.START_Z+1.15;world.y=world.floor();world.interact();advance(world,300,0,1,false,0);camera.reset();camera.distance=6;camera.yaw=.65;
            ImageIO.write(renderer.render(world,camera),"png",new File("out/windward-tests/pushing.png"));
            while(world.puzzle.stoneZ>MountainPuzzle.PLATE_Z+.1)world.step(0,1,false,0);
            advance(world,125,0,0,false,0);world.combat.enemyHealth=0;world.x=MountainPuzzle.TERMINAL_X;world.z=MountainPuzzle.TERMINAL_Z+1.4;world.y=world.floor();world.interact();camera.reset();camera.yaw=.4;camera.pitch=.4;camera.distance=10;
            ImageIO.write(renderer.render(world,camera),"png",new File("out/windward-tests/tower-active.png"));
            world.x=0;world.z=12;world.y=world.floor();camera.reset();
            ImageIO.write(renderer.render(world,camera),"png",new File("out/windward-tests/tower-beacon.png"));
            for(int i=0;i<4;i++){
                world.reset();world.x=i==0?0:i==1?Math.PI*MountainPlanet.RADIUS-2:i==2?700:1400;
                world.z=i==2?-Math.PI*MountainPlanet.RADIUS/2+.5:i==3?900:12;world.y=world.floor();camera.reset();
                camera.globe=i==0;
                BufferedImage planet=renderer.render(world,camera);
                ImageIO.write(planet,"png",new File("out/windward-tests/planet-"+i+".png"));
                renderer.culling=false;BufferedImage reference=renderer.render(world,camera);renderer.culling=true;
                BufferedImage culled=renderer.render(world,camera);
                check(java.util.Arrays.equals(((java.awt.image.DataBufferInt)reference.getRaster().getDataBuffer()).getData(),((java.awt.image.DataBufferInt)culled.getRaster().getDataBuffer()).getData()),"Spherical frustum preserves pixels at planet location "+i);
                check(renderer.terrainSize()<700,"Planet GPU cache bounded after travelling");
            }
            check(org.lwjgl.opengl.GL11.glGetError()==0,"No GL errors at sphere seams/poles");
            for(int i=0;i<3;i++){
                world.reset();world.x=i==0?120:i==1?175:350;world.z=-130;world.y=world.floor();camera.reset();
                camera.yaw=i==0?-Math.PI/2:i==1?.5:2;camera.pitch=i==0?.45:.3;camera.distance=i==0?15:9;
                BufferedImage view=renderer.render(world,camera);ImageIO.write(view,"png",new File("out/windward-tests/landscape-"+i+".png"));
                renderer.culling=false;BufferedImage reference=renderer.render(world,camera);renderer.culling=true;
                BufferedImage culled=renderer.render(world,camera);
                check(java.util.Arrays.equals(((java.awt.image.DataBufferInt)reference.getRaster().getDataBuffer()).getData(),((java.awt.image.DataBufferInt)culled.getRaster().getDataBuffer()).getData()),"Water and habitat culling preserves pixels "+i);
                if(i==1){
                    int blue=0;for(int y=200;y<550;y++)for(int x=100;x<800;x++){
                        int rgb=view.getRGB(x,y),r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;
                        if(b>r*1.3&&g>r*1.3&&b>85)blue++;
                    }
                    check(blue>10000,"Lake water visibly covers the foreground, not just sky");
                }
            }
            check(org.lwjgl.opengl.GL11.glGetError()==0,"No GL errors drawing water and habitat variations");
            world.reset();world.x=4.2;world.z=8;world.y=world.floor();camera.reset();camera.distance=3;camera.pitch=.15;
            ImageIO.write(renderer.render(world,camera),"png",new File("out/windward-tests/butterflies.png"));
            world.x=19;world.z=-9;world.y=MountainWildlife.sample(new MountainWildlife.Spawn(0,-12,0,true),0,19,-9).y-1.35;
            camera.reset();camera.distance=5;camera.pitch=.12;
            ImageIO.write(renderer.render(world,camera),"png",new File("out/windward-tests/birds.png"));
            world.reset();camera.reset();
            long[] times=new long[30];
            for(int i=0;i<times.length;i++){long t=System.nanoTime();renderer.render(world,camera);times[i]=System.nanoTime()-t;}
            java.util.Arrays.sort(times);
            System.out.printf(java.util.Locale.ROOT,"GL: %s; 900x600 render/readback median %.2f ms, p95 %.2f ms; warmup %.0f ms%n",renderer.device,times[15]/1e6,times[28]/1e6,(System.nanoTime()-start)/1e6);
        }
        System.out.println("PASS: real OpenGL scenes and render/readback measurement");
    }
}
