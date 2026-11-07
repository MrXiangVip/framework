package com.packages.ebook;
import com.android.app.Activity;
public final class MainActivity extends Activity {
    private EbookView reader;
    @Override protected void onCreate() { super.onCreate(); getWindow().setLayout(640, 800); reader = new EbookView(this); setContentView(reader); }
    @Override protected void onPause() { if (reader != null) reader.save(); super.onPause(); }
    @Override protected void onDestroy() { if (reader != null) reader.close(); super.onDestroy(); }
}
