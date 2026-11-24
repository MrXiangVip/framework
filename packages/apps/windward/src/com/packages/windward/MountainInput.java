package com.packages.windward;
import java.util.HashSet;
import java.util.Set;
import com.android.view.KeyEvent;
/** Only touched by UI; returned state is copied to the worker through commands. */
final class MountainInput {
    private final Set<Integer> held=new HashSet<>();
    static int normalize(int key){return key>='A'&&key<='Z'?key+32:key;}
    boolean press(int key){return held.add(normalize(key));}
    void release(int key){held.remove(normalize(key));}
    boolean down(int key){return held.contains(key);}
    int state(){return ((down('w')||down(KeyEvent.ARROW_UP))?1:0)|((down('s')||down(KeyEvent.ARROW_DOWN))?2:0)|((down('a')||down(KeyEvent.LEFT))?4:0)|((down('d')||down(KeyEvent.RIGHT))?8:0)|((down(0xffe1)||down(0xffe2))?16:0);}
    void clear(){held.clear();}
}
