package com.packages.launcher;

import com.android.graphics.Canvas;
import com.android.view.MotionEvent;
import com.android.view.View;
import com.android.widget.GridLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

public final class DesktopPagesTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void event(DesktopPages d, int action, int x, int y) {
        check(d.dispatchTouchEvent(new MotionEvent(action, x, y)), "Gesture handled");
    }
    static void finish(DesktopPages d) { d.advanceAnimation(System.nanoTime() + 1000000000L); }
    static void swipe(DesktopPages d, int from, int to, int action) {
        event(d, MotionEvent.ACTION_DOWN, from, 80);
        event(d, MotionEvent.ACTION_MOVE, to, 80);
        event(d, action, to, 80); finish(d);
    }
    static BufferedImage render(DesktopPages d) {
        BufferedImage image = new BufferedImage(640, 800, BufferedImage.TYPE_INT_RGB);
        try (Canvas canvas = new Canvas(image)) { d.draw(canvas); }
        return image;
    }
    public static void main(String[] args) throws Exception {
        List<GridLayout> grids = new ArrayList<>(); int[] clicks = {0};
        int[] colors = {0xffb8d7fa, 0xffbfe5c4, 0xffffd4a6};
        for (int i = 0; i < 3; i++) {
            final int color = colors[i];
            GridLayout grid = new GridLayout(null) {
                @Override protected void onDraw(Canvas canvas) { canvas.fillRect(0,0,getWidth(),getHeight(),color); }
            };
            View icon = new View(null) {
                @Override protected void onDraw(Canvas canvas) { canvas.fillRect(0,0,getWidth(),getHeight(),color); }
            };
            icon.setOnClickListener(v -> clicks[0]++);
            grid.addView(icon, new com.android.view.ViewGroup.LayoutParams(640, 700)); grids.add(grid);
        }
        DesktopPages d = new DesktopPages(null, grids);
        d.measure(View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)); d.layout(0, 0, 640, 800);
        event(d, MotionEvent.ACTION_DOWN, 30, 80); event(d, MotionEvent.ACTION_UP, 30, 80);
        check(clicks[0] == 1, "Tap opens icon");
        event(d, MotionEvent.ACTION_DOWN, 500, 80); event(d, MotionEvent.ACTION_MOVE, 180, 80);
        check(grids.get(0).getLeft() == -320 && grids.get(1).getLeft() == 320, "Adjacent grids move on the same track");
        BufferedImage image = render(d);
        check(image.getRGB(100,100) == colors[0] && image.getRGB(500,100) == colors[1], "Both pages visible during swipe");
        new File("out/launcher-tests").mkdirs(); ImageIO.write(image,"png",new File("out/launcher-tests/swipe-between-pages.png"));
        event(d, MotionEvent.ACTION_UP, 180, 80);
        check(d.isSettling() && d.currentPage() == 0, "Release starts animation instead of jumping");
        event(d,MotionEvent.ACTION_DOWN,30,80);event(d,MotionEvent.ACTION_UP,30,80);
        check(clicks[0]==1,"Moving icons cannot launch during snap");
        d.advanceAnimation(System.nanoTime()+80000000L);
        check(grids.get(0).getLeft() < -320 && grids.get(1).getLeft() > 0, "Snap animates both pages"); finish(d);
        check(d.currentPage() == 1 && clicks[0] == 1 && grids.get(1).getLeft() == 0, "Snap finishes without launching");
        swipe(d,30,60,MotionEvent.ACTION_UP);
        check(d.currentPage()==1 && clicks[0]==1,"Short drag returns without launch");
        event(d,MotionEvent.ACTION_DOWN,100,80);event(d,MotionEvent.ACTION_MOVE,420,80);
        image=render(d);check(image.getRGB(100,100)==colors[0] && image.getRGB(500,100)==colors[1],"Previous page is visible on right drag");
        event(d,MotionEvent.ACTION_CANCEL,420,80);finish(d);
        check(d.currentPage()==1 && grids.get(1).getLeft()==0,"Cancel snaps back");
        swipe(d,30,160,MotionEvent.ACTION_UP);check(d.currentPage()==0,"Right swipe returns");
        swipe(d,30,160,MotionEvent.ACTION_UP);check(d.currentPage()==0,"First boundary");
        d.showPage(2);finish(d);swipe(d,160,0,MotionEvent.ACTION_UP);check(d.currentPage()==2,"Last boundary");
        event(d,MotionEvent.ACTION_DOWN,30,40);event(d,MotionEvent.ACTION_MOVE,30,90);event(d,MotionEvent.ACTION_UP,30,90);
        check(d.currentPage()==2 && clicks[0]==1,"Vertical drag cannot launch");
        d.showPage(1);finish(d);
        event(d,MotionEvent.ACTION_DOWN,400,730);event(d,MotionEvent.ACTION_MOVE,100,730);event(d,MotionEvent.ACTION_UP,100,730);finish(d);
        check(d.currentPage()==2,"Empty desktop space supports swipe");
        d.showPage(1);finish(d);event(d,MotionEvent.ACTION_DOWN,500,80);event(d,MotionEvent.ACTION_MOVE,180,80);
        d.layout(0,0,600,800);check(!d.isSettling() && grids.get(1).getLeft()==0,"Resize cancels drag cleanly");
        d.next.performClick();finish(d);check(d.currentPage()==2,"Next button uses animated track");
        d.previous.performClick();finish(d);check(d.currentPage()==1,"Previous button uses animated track");
        event(d,MotionEvent.ACTION_DOWN,30,80);event(d,MotionEvent.ACTION_UP,30,80);
        check(clicks[0]==2,"Settled destination icons remain clickable");
        DesktopPages single = new DesktopPages(null, Collections.singletonList(new GridLayout(null)));
        single.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));
        single.layout(0,0,640,800);single.showPage(1);finish(single);
        check(single.currentPage()==0 && single.getChildCount()==1,"Single or empty app page needs no navigation");
        System.out.println("PASS: simultaneous adjacent-page pixels, animated snap, taps, short/vertical drags, edges, cancellation, empty space and resize");
    }
}
