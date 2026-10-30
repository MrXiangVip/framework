package com.packages.kof;
import com.android.content.*;
import com.android.res.Resources;
import com.android.os.Looper;
import com.android.view.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Headless integration tests use the real packaged hero images and real keyboard/touch input. */
public final class HeroRosterTest {
 private static int checks;
 private static final Context CONTEXT=new Context(){public Resources getResources(){return null;}public Intent registerReceiver(BroadcastReceiver r,IntentFilter f){return null;}public ComponentName startService(Intent i){return null;}public ContentResolver getContentResolver(){return null;}};
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static void down(KofView v,int k){v.dispatchKeyEvent(new KeyEvent(KeyEvent.DOWN,k,0));}
 private static void up(KofView v,int k){v.dispatchKeyEvent(new KeyEvent(KeyEvent.UP,k,0));}
 private static void press(KofView v,int k){down(v,k);up(v,k);}
 private static void tick(KofView v,int n){for(int i=0;i<n;i++)v.advance(1.0/60);}
 private static void click(KofView v,int x,int y){v.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN,x,y));v.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_UP,x,y));}
 private static void render(KofView v,File dir,String name)throws Exception{ImageIO.write(ViewRootImpl.renderToImage(v,1280,720),"png",new File(dir,name+".png"));}
 private static FighterSprites actor(KofView v,int slot)throws Exception{java.lang.reflect.Field f=KofView.class.getDeclaredField("actors");f.setAccessible(true);return ((FighterSprites[])f.get(v))[slot];}
 private static void roster(KofView v,GameSession.Mode mode,int id){v.getSession().back();v.getSession().setMode(mode);press(v,KeyEvent.ENTER);press(v,'1'+id);}
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();System.setProperty("framework.kof.sound","false");File dir=new File(args[0]);dir.mkdirs();
  for(GameSession.Mode mode:GameSession.Mode.values())for(int id=0;id<2;id++){
   GameSession s=new GameSession();s.setMode(mode);s.openRoster();check(s.getRosterSize()==2,"both heroes available in "+mode);s.select(id);s.start();
   if(mode==GameSession.Mode.VERSUS){check(s.getScreen()==GameSession.Screen.ROSTER&&s.getSelectingPlayer()==1,"versus confirms 1P before 2P");s.select(id);s.start();check(s.getOpponent()==id,"mirror matches allowed for "+id);}
   check(s.getSelected()==id&&s.getScreen()==GameSession.Screen.BATTLE,"hero starts "+mode+" "+id);
   if(mode==GameSession.Mode.CPU)check(s.getOpponent()==1-id,"CPU picks the other hero");
  }
  KofView v=new KofView(CONTEXT,null);render(v,dir,"heroes-home");
  int[] attackKeys={'j','k','l',';'};
  FighterSprites.Action[] attackActions={FighterSprites.Action.LIGHT_PUNCH,FighterSprites.Action.LIGHT_KICK,FighterSprites.Action.HEAVY_PUNCH,FighterSprites.Action.HEAVY_KICK};
  for(int id=0;id<2;id++){
   String name=GameSession.SPRITE_DIRECTORIES[id];roster(v,GameSession.Mode.TRAINING,id);
   click(v,id==0?530:740,577);check(v.getSession().getSelected()==id,"mouse selects "+name);render(v,dir,name+"-roster");
   press(v,KeyEvent.ENTER);tick(v,2);FightEngine f=v.getSession().getFight();FighterSprites sprites=actor(v,0);
   check(f.isTraining()&&f.getState()==FightEngine.State.FIGHT,"solo training "+name);
   for(FighterSprites.Action a:FighterSprites.Action.values())check(sprites.frameCount(a)==(id==0&&a!=FighterSprites.Action.LIGHT_KICK&&a!=FighterSprites.Action.HEAVY_KICK?8:6),"real frame count "+name+"/"+a);
   render(v,dir,name+"-idle");int first=sprites.frameIndex(f.getPlayer());tick(v,10);check(sprites.frameIndex(f.getPlayer())!=first,"idle animates "+name);
   down(v,'d');tick(v,9);check(sprites.getAction()==FighterSprites.Action.WALK&&f.getPlayer().isMoving(),"walking animation "+name);render(v,dir,name+"-walk");up(v,'d');tick(v,1);
   down(v,'a');tick(v,4);check(f.getPlayer().getFacing()==-1,"left-facing mirror "+name);render(v,dir,name+"-left");up(v,'a');press(v,'r');
   press(v,'w');tick(v,13);check(sprites.getAction()==FighterSprites.Action.JUMP,"jump animation "+name);render(v,dir,name+"-jump");tick(v,60);
   down(v,'s');tick(v,25);check(sprites.getAction()==FighterSprites.Action.CROUCH&&sprites.frameIndex(f.getPlayer())==(id==0?7:4),"crouch holds final frame "+name);render(v,dir,name+"-crouch");
   if(id==1){BufferedImage feet=new BufferedImage(700,400,BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D fg=feet.createGraphics();fg.translate(300,350);sprites.draw(fg,f.getPlayer(),1);fg.dispose();for(int y=343;y<350;y++)check((feet.getRGB(300,y)>>>24)==0,"crouch has no baked floor line between feet");}
   up(v,'s');tick(v,1);
   for(int i=0;i<attackKeys.length;i++){
    press(v,attackKeys[i]);tick(v,1);check(sprites.getAction()==attackActions[i],"attack animation "+name+"/"+attackActions[i]);first=sprites.frameIndex(f.getPlayer());
    tick(v,(int)Math.ceil((f.getPlayer().getMove().windup+f.getPlayer().getMove().active*.5)*60)-1);
    check(sprites.frameIndex(f.getPlayer())>first,"attack frames progress "+name+"/"+attackActions[i]);render(v,dir,name+"-"+attackActions[i].name().toLowerCase(java.util.Locale.ROOT));
    BufferedImage cutout=new BufferedImage(700,400,BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D g=cutout.createGraphics();g.translate(300,350);sprites.draw(g,f.getPlayer(),1);g.dispose();
    check((cutout.getRGB(0,350)>>>24)==0&&(cutout.getRGB(699,350)>>>24)==0,"no opaque sprite rectangle "+name+"/"+attackActions[i]);ImageIO.write(cutout,"png",new File(dir,name+"-cutout-"+i+".png"));
    tick(v,60);check(f.getPlayer().getMove()==null&&sprites.getAction()==FighterSprites.Action.IDLE,"attack returns idle "+name+"/"+attackActions[i]);
   }
   press(v,'u');check(f.getPlayer().getMove()==null,"no unsupported special "+name);
   down(v,'d');tick(v,4);press(v,'p');first=sprites.frameIndex(f.getPlayer());double x=f.getPlayer().getX();tick(v,45);
   check(sprites.frameIndex(f.getPlayer())==first&&f.getPlayer().getX()==x,"pause freezes simulation and animation "+name);up(v,'d');press(v,'p');tick(v,1);
   tick(v,4000);check(v.getSession().getScreen()==GameSession.Screen.BATTLE&&f.getSeconds()==0&&f.getEnemy().getHealth()==100,"practice remains solo and untimed "+name);
   click(v,1200,650);check(f.getPlayer().getX()==360&&sprites.getAction()==FighterSprites.Action.IDLE,"training reset "+name);
  }
  for(int id=0;id<2;id++){
   roster(v,GameSession.Mode.CPU,id);press(v,KeyEvent.ENTER);tick(v,90);check(v.getSession().getOpponent()==1-id,"real CPU pairing "+id);render(v,dir,"cpu-"+id);
   boolean attacked=false;for(int t=0;t<180;t++){tick(v,1);FightEngine.Move move=v.getSession().getFight().getEnemy().getMove();check(move!=FightEngine.Move.SPECIAL,"CPU uses supplied attack actions");attacked|=move!=null;}
   check(attacked,"CPU controls sprite opponent "+id);double x=v.getSession().getFight().getPlayer().getX();click(v,1200,650);check(v.getSession().getFight().getPlayer().getX()==x,"hidden training reset cannot restart CPU bout");
  }
  for(int id=0;id<2;id++){
   roster(v,GameSession.Mode.VERSUS,id);press(v,KeyEvent.ENTER);press(v,'1'+id);press(v,KeyEvent.ENTER);tick(v,90);FightEngine f=v.getSession().getFight();
   FighterSprites p=actor(v,0),q=actor(v,1);check(p!=q,"mirror fighters have independent animation players "+id);
   down(v,'s');press(v,KeyEvent.ARROW_UP);tick(v,10);check(p.getAction()==FighterSprites.Action.CROUCH&&q.getAction()==FighterSprites.Action.JUMP,"independent simultaneous crouch/jump "+id);render(v,dir,"versus-mirror-"+id);up(v,'s');tick(v,70);
   FightEngine.Move[] moves={FightEngine.Move.LIGHT_PUNCH,FightEngine.Move.LIGHT_KICK,FightEngine.Move.PUNCH,FightEngine.Move.KICK};
   for(int i=0;i<4;i++){press(v,'1'+i);tick(v,1);check(f.getEnemy().getMove()==moves[i]&&q.getAction()==attackActions[i],"2P four attack bindings "+id+"/"+i);check(p.getAction()==FighterSprites.Action.IDLE,"2P attacks do not change 1P animation "+id+"/"+i);tick(v,70);}
   down(v,'a');down(v,'s');down(v,KeyEvent.RIGHT);down(v,KeyEvent.ARROW_DOWN);tick(v,1);check(f.getPlayer().isGuarding()&&f.getPlayer().isCrouching()&&f.getEnemy().isGuarding()&&f.getEnemy().isCrouching(),"both heroes crouch guard "+id);up(v,'a');up(v,'s');up(v,KeyEvent.RIGHT);up(v,KeyEvent.ARROW_DOWN);
  }
  System.out.println("HeroRosterTest: "+checks+" checks passed; previews: "+dir);
 }
}
