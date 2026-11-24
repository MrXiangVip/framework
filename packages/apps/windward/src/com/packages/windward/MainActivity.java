package com.packages.windward;
import com.android.app.Activity;
public final class MainActivity extends Activity {
    private MountainView game;
    @Override protected void onCreate() {
        super.onCreate(); getWindow().setLayout(1200,800);
        game=new MountainView(this); setContentView(game);
    }
    @Override protected void onPause() { if(game!=null)game.pause(); super.onPause(); }
    @Override protected void onDestroy() { if(game!=null)game.close(); super.onDestroy(); }
}
