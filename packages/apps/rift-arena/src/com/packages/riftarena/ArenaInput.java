package com.packages.riftarena;

import com.android.view.KeyEvent;
import java.util.LinkedHashSet;
import java.util.Set;

/** Physical held keys in press order; repeats never take priority from a newer direction. */
final class ArenaInput {
    private final Set<Integer> held=new LinkedHashSet<>();
    synchronized boolean add(int key){return held.add(key);}
    synchronized void remove(int key){held.remove(key);}
    synchronized void clear(){held.clear();}
    synchronized int snapshot(){
        int x=0,z=0;
        for(int key:held){
            if(key=='a'||key==KeyEvent.LEFT)x=-1;
            else if(key=='d'||key==KeyEvent.RIGHT)x=1;
            else if(key=='w'||key==KeyEvent.ARROW_UP)z=-1;
            else if(key=='s'||key==KeyEvent.ARROW_DOWN)z=1;
        }
        return (x+1)|((z+1)<<2)|(held.contains(32)?16:0);
    }
    static int x(int state){return (state&3)-1;}
    static int z(int state){return ((state>>2)&3)-1;}
}
