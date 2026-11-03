/** Run under Xvfb with an 800x600 display and the window JNI library. */
package com.android.view;
import com.android.os.*;
import com.android.view.*;
public class WindowSizeCallbackTest {
 public static void main(String[] args) {
  Looper.prepareMainLooper();
  View view = new View(null) {
   protected void onMeasure(int w, int h) { setMeasuredDimension(resolveSize(123,w),resolveSize(87,h)); }
  };
  final int[] reported = new int[2];
  ViewRootImpl root = new ViewRootImpl(null, 0);
  root.setWindowCallback(new Window.Callback() {
   public void onWindowMinimizedChanged(boolean minimized) {}
   public void onWindowSizeChanged(int width, int height) {
    reported[0] = width; reported[1] = height;
   }
  });
  root.setView(view,new WindowManager.LayoutParams(-2,-2), null, 0);
  if(reported[0]!=123 || reported[1]!=87) throw new AssertionError("Initial callback");
  Handler handler = new Handler() {
   int stage;
   public void handleMessage(Message message) {
    try {
     if(stage==0) {
      if(view.getWidth()!=123 || view.getHeight()!=87) throw new AssertionError("Initial wrap size");
      root.setLayoutParams(new WindowManager.LayoutParams(420,240));
      stage++; sendMessageDelayed(obtainMessage(0,null),200);
     } else if(stage==1) {
      if(view.getWidth()!=420 || view.getHeight()!=240 || reported[0]!=420 || reported[1]!=240) throw new AssertionError("Updated fixed size");
      root.setLayoutParams(new WindowManager.LayoutParams(-1,-1));
      stage++; sendMessageDelayed(obtainMessage(0,null),200);
     } else {
      if(view.getWidth()!=800 || view.getHeight()!=600 || reported[0]!=800 || reported[1]!=600) throw new AssertionError("Match display");
      root.close(); System.out.println("WindowSizeCallbackTest passed"); System.exit(0);
     }
    } catch(Throwable t) { t.printStackTrace(); System.exit(1); }
   }
  };
  handler.sendMessageDelayed(handler.obtainMessage(0,null),200);
  Looper.loop();
 }
}
