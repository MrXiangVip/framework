package fixture.multi;

import com.android.app.Activity;
import com.android.view.View;
import java.io.*;
import java.lang.management.ManagementFactory;

public class Shared extends Activity {
    private static int nextId;
    private final int id = ++nextId;

    private void record(String event) {
        try (FileWriter out = new FileWriter(System.getProperty("framework.multi.journal"), true)) {
            out.write(ManagementFactory.getRuntimeMXBean().getName().split("@")[0] + " "
                    + id + " " + getClass().getSimpleName() + " " + event + " "
                    + Thread.currentThread().getName() + "\n");
        } catch (IOException failure) { throw new RuntimeException(failure); }
    }

    @Override protected void onCreate() {
        record("create");
        View content = new View(this);
        content.setOnClickListener(view -> getWindowManager().removeView(getWindow().getDecorView()));
        setContentView(content);
        if (id == 1) startActivity("fixture.multi.Second");
    }
    @Override protected void onResume() { record("resume"); }
    @Override protected void onPause() { record("pause"); }
    @Override protected void onDestroy() { record("destroy"); }
}
