package com.packages.settings;

import com.android.am.ActivityManagerClient;
import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.os.Handler;
import com.android.util.AttributeSet;
import com.android.view.*;
import com.android.widget.EditText;
import com.android.widget.Button;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/** Settings preferences, actual packaged applications, and read-only host information. */
public class SettingsView extends ViewGroup {
    private boolean updatingInputs;
    private final Path file;
    private final SettingsStore store;
    private SettingsStore.Values values = SettingsStore.Values.defaults();
    private String theme = "light", status = "Loading settings...", section = "general";
    private int textSize = 16, page, selected = -1, capacity = 5, inset = 24, step = 80;
    private boolean clock24 = true, busy, dirty, confirming;
    private List<SettingsInfo.App> apps = new ArrayList<>();
    private List<String[]> device = new ArrayList<>();
    private final Map<String, Control> controls = new LinkedHashMap<>();
    private final Row[] rows = new Row[8];
    private final EditText input;
    private final Handler ui = new Handler(message -> { ((Runnable) message.obj).run(); return true; });
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> { Thread worker = new Thread(r, "settings-io"); worker.setDaemon(true); return worker; });


    public SettingsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        file = Paths.get(System.getProperty("framework.settings.file", Paths.get(System.getProperty("user.home"), ".framework", "settings.properties").toString())).toAbsolutePath().normalize();
        store = new SettingsStore(file);
        control("general", "Preferences", () -> section("general")); control("apps", "Apps", () -> section("apps")); control("device", "Device", () -> section("device"));
        control("theme", "", () -> { theme = theme.equals("light") ? "dark" : "light"; changed(); });
        control("font", "", () -> { textSize = textSize == 18 ? 14 : textSize + 2; changed(); });
        control("clock", "", () -> { clock24 = !clock24; changed(); });
        control("save", "Save", this::save); control("reload", "Reload", this::load);
        control("reset", "Reset", () -> { confirming = true; clearFocus(); requestLayout(); invalidate(); });
        control("confirm", "Reset settings", () -> persist(SettingsStore.Values.defaults(), "Defaults restored"));
        control("cancel", "Cancel", () -> { confirming = false; requestLayout(); invalidate(); });
        control("refresh", "Refresh", this::refresh);
        control("previous", "< Previous", () -> { if (page > 0) { page--; selected = -1; invalidate(); } });
        control("next", "Next >", () -> { if ((page + 1) * capacity < count()) { page++; selected = -1; invalidate(); } });
        control("open", "Open app", this::open);
        input = new EditText(context) {
            @Override protected void onDraw(Canvas canvas) {
                canvas.fillRect(0, 0, getWidth(), getHeight(), card()); mTextColor = ink(); super.onDraw(canvas);
            }
        };
        input.setId("name"); input.setSingleLine(); input.setMaxCodePoints(40); input.setTextSize(14);
        input.setOnTextChangedListener((view, text) -> { if (!updatingInputs) changed(); });
        addView(input, new LayoutParams(-2, -2));
        for (int i = 0; i < rows.length; i++) { rows[i] = new Row(context, i); rows[i].setId("row_" + i); addView(rows[i], new LayoutParams(-2, -2)); }
        apply(values); load();
    }
    private void control(String id, String label, Runnable action) {
        Control button = new Control(getContext(), id, label); button.setId(id);
        button.setOnClickListener(v -> { if (!busy) action.run(); });
        controls.put(id, button); addView(button, new LayoutParams(-2, -2));
    }
    private interface Work { Runnable run() throws Exception; }
    private void work(String description, Work work) {
        if (busy) return;
        busy = true; status = description; invalidate();
        io.execute(() -> {
            Runnable action;
            try { action = work.run(); }
            catch (Exception e) { String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); action = () -> status = "Error: " + error; }
            final Runnable done = action;
            ui.sendMessage(ui.obtainMessage(0, (Runnable) () -> { busy = false; done.run(); requestLayout(); invalidate(); }));
        });
    }
    private void apply(SettingsStore.Values saved) { values = saved; theme = saved.theme; textSize = saved.textSize; clock24 = saved.clock24; setInput(input, saved.name); dirty = false; clearFocus(); }
    private void load() { work("Loading settings...", () -> { SettingsStore.Values saved = store.load(); return () -> { apply(saved); confirming = false; status = "Settings loaded"; }; }); }
    private void save() {
        try { persist(new SettingsStore.Values(input.getText(), theme, textSize, clock24), "Settings saved"); }
        catch (IllegalArgumentException e) { status = e.getMessage(); invalidate(); }
    }
    private void persist(SettingsStore.Values saved, String message) { work("Saving settings...", () -> { store.save(saved); return () -> { apply(saved); confirming = false; status = message; }; }); }
    private void changed() { dirty = true; status = "Unsaved changes"; invalidate(); }
    private void section(String value) { section = value; page = 0; selected = -1; clearFocus(); confirming = false; requestLayout(); invalidate(); if (!value.equals("general")) refresh(); }
    private void refresh() {
        if (section.equals("apps")) work("Reading applications...", () -> { List<SettingsInfo.App> found = SettingsInfo.apps(SettingsInfo.appDirectory()); return () -> { apps = found; page = 0; selected = -1; status = found.size() + " packaged applications"; }; });
        else if (section.equals("device")) work("Reading device information...", () -> { List<String[]> found = SettingsInfo.device(file); return () -> { device = found; page = 0; status = "Host information refreshed"; }; });
    }
    private int count() { return section.equals("apps") ? apps.size() : device.size(); }
    private void open() {
        if (selected < 0 || selected >= apps.size()) { status = "Select an application first"; invalidate(); return; }
        SettingsInfo.App app = apps.get(selected);
        if (app.activity.isEmpty()) status = "This application has no launcher activity";
        else work("Requesting launch...", () -> {
            ActivityManagerClient.startActivity(app.activity);
            return () -> status = "Launch requested: " + app.name;
        });
        invalidate();
    }
    public boolean isBusy() { return busy; }
    public String getStatus() { return status; }
    public String getTheme() { return theme; }
    public int getTextSize() { return textSize; }
    public boolean is24Hour() { return clock24; }
    public String getDeviceName() { return input.getText(); }
    public int getAppCount() { return apps.size(); }
    public int getDeviceCount() { return device.size(); }
    public boolean isDirty() { return dirty; }
    public boolean isConfirming() { return confirming; }
    public String getRowName(int row) { int index = page * capacity + row; return index >= count() ? "" : section.equals("apps") ? apps.get(index).name : device.get(index)[0]; }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (busy && event.getAction() != MotionEvent.ACTION_CANCEL) return true;
        return super.dispatchTouchEvent(event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (busy) return true;
        if (event.getType() == KeyEvent.DOWN && event.getValue() == KeyEvent.ESCAPE) {
            if (confirming) { confirming = false; requestLayout(); } else { clearFocus(); }
            invalidate(); return true;
        }
        if (section.equals("general") && !confirming && event.getType() == KeyEvent.DOWN
                && (event.getValue() == KeyEvent.TAB || event.getValue() == KeyEvent.ISO_LEFT_TAB)) return input.requestFocus();
        if (section.equals("general") && !confirming && input.isFocused()) {
            if (event.getType() == KeyEvent.DOWN && event.getValue() == KeyEvent.ENTER) { save(); return true; }
            if (input.dispatchKeyEvent(event)) return true;
        }
        return false;
    }
    private void setInput(EditText field, String value) {
        updatingInputs = true;
        try {
            field.setText(value);
            field.setSelection(field.getText().length());
        } finally { updatingInputs = false; }
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(600, widthSpec), resolveSize(800, heightSpec));
        inset = Math.min(24, getMeasuredWidth() / 20);
        step = Math.max(42, Math.min(88, (getMeasuredHeight() - 280) / 4));
        int oldCapacity = capacity; capacity = Math.max(1, Math.min(8, (getMeasuredHeight() - 272) / 82));
        if (oldCapacity != capacity) { page = Math.min(page, Math.max(0, (count() - 1) / capacity)); selected = -1; }
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(42, MeasureSpec.EXACTLY));
    }
    private void place(View view, int x, int y, int width, int height) { view.layout(x, y, x + Math.max(0, width), y + Math.max(0, height)); }
    private void place(String id, int x, int y, int width, int height) { place(controls.get(id), x, y, width, height); }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) place(getChildAt(i), 0, 0, 0, 0);
        int width = getWidth() - inset * 2, third = (width - 16) / 3, bottom = getHeight() - 98;
        String[] tabs = {"general", "apps", "device"};
        for (int i = 0; i < 3; i++) place(tabs[i], inset + i * (third + 8), 96, third, 40);
        if (confirming) {
            place("confirm", inset, 310, width, 42); place("cancel", inset, 364, width, 42); return;
        }
        if (section.equals("general")) {
            String[] ids = {"theme", "font", "clock"}; int controlWidth = Math.min(122, width / 3);
            for (int i = 0; i < 3; i++) place(ids[i], getWidth() - inset - controlWidth, 160 + i * step, controlWidth, 38);
            place(input, inset, 160 + step * 3 + 22, width, 36);
            String[] actions = {"save", "reload", "reset"};
            for (int i = 0; i < 3; i++) place(actions[i], inset + i * (third + 8), bottom, third, 40);
        } else {
            for (int i = 0; i < capacity; i++) place(rows[i], inset, 160 + i * 82, width, 74);
            place("previous", inset, bottom, third, 36); place("next", getWidth() - inset - third, bottom, third, 36);
            place("refresh", inset, bottom + 46, third, 32);
            if (section.equals("apps")) place("open", getWidth() - inset - third, bottom + 46, third, 32);
        }
    }
    private boolean dark() { return theme.equals("dark"); }
    private int ink() { return dark() ? 0xffedf2fc : 0xff182b49; }
    private int muted() { return dark() ? 0xffa7b4ca : 0xff66758a; }
    private int card() { return dark() ? 0xff202e43 : 0xffffffff; }
    private int accent() { return dark() ? 0xff92bcff : 0xff2c60b9; }
    @Override protected void onDraw(Canvas canvas) {
        canvas.fillRect(0, 0, getWidth(), getHeight(), dark() ? 0xff121d2e : 0xfff3f6fc);
        canvas.drawText("SETTINGS", inset, 40, 25, ink());
        canvas.drawText(fit(input.getText(), getWidth() - inset * 2, 14), inset, 67, 14, muted());
        if (confirming) {
            canvas.drawText("Restore default preferences?", inset, 200, Math.min(22, getWidth() / 16), ink());
            canvas.drawText("Only saved Settings preferences are reset.", inset, 240, Math.min(15, getWidth() / 27), muted());
            canvas.drawText("Other apps and host settings are preserved.", inset, 269, Math.min(15, getWidth() / 27), muted());
        } else if (section.equals("general")) {
            String[] labels = {"Appearance", "Text size", "Time format"};
            String[] details = {"Settings interface", "Settings interface", new SimpleDateFormat(clock24 ? "HH:mm" : "hh:mm a", Locale.ROOT).format(new Date())};
            for (int i = 0; i < 3; i++) {
                canvas.drawText(labels[i], inset, 178 + i * step, textSize, ink());
                canvas.drawText(details[i], inset, 197 + i * step, 12, muted());
            }
            canvas.drawText("Device label (local)", inset, 176 + step * 3, 14, muted());
            int preview = 160 + step * 3 + 88;
            if (preview < getHeight() - 133) {
                canvas.fillRect(inset, preview, getWidth() - inset * 2, 54, card());
                canvas.drawText("Aa  Preview", inset + 16, preview + 34, textSize, ink());
            }
            canvas.drawText(fit(status, getWidth() - inset * 2, 12), inset, getHeight() - 38, 12, accent());
            canvas.drawText(dirty ? "Changes apply here; Save to keep them." : "Preferences apply to the Settings app.", inset, getHeight() - 17, Math.min(12, getWidth() / 28), muted());
        } else {
            if (count() == 0) canvas.drawText(busy ? "Loading..." : "No items to display", inset, 200, textSize, muted());
            canvas.drawText((page + 1) + " / " + Math.max(1, (count() + capacity - 1) / capacity), getWidth() / 2 - 18, getHeight() - 74, 14, muted());
            int available = getWidth() - inset * 2;
            if (section.equals("apps")) available -= (available - 16) / 3 + 10;
            canvas.drawText(fit(status, Math.max(0, available), 11), inset + (getWidth() - inset * 2 - 16) / 3 + 10, getHeight() - 32, 11, accent());
        }
    }
    private static String fit(String value, int width, int size) {
        if (value == null) return "";
        int end = value.length();
        while (end > 0 && com.android.graphics.FontManager.measureText(value.substring(0, end) + (end < value.length() ? "..." : ""), size) > width) end = value.offsetByCodePoints(end, -1);
        return value.substring(0, end) + (end < value.length() ? "..." : "");
    }
    private final class Row extends View {
        private final int row;
        Row(Context context, int row) { super(context); this.row = row; setOnClickListener(v -> { if (!busy && section.equals("apps") && page * capacity + row < apps.size()) { selected = page * capacity + row; status = "Selected: " + apps.get(selected).packageName; invalidate(); } }); }
        @Override protected void onDraw(Canvas canvas) {
            int index = page * capacity + row; if (index >= count()) return;
            canvas.fillRect(0, 0, getWidth(), getHeight(), card());
            if (section.equals("apps") && selected == index) canvas.strokeRect(0, 0, getWidth(), getHeight(), accent());
            String title, detail, sub;
            if (section.equals("apps")) { SettingsInfo.App app = apps.get(index); title = app.name; detail = app.packageName; sub = "Version " + (app.version == null ? "unknown" : app.version) + " / " + SettingsInfo.format(app.bytes); }
            else { String[] info = device.get(index); title = info[0]; detail = info[1]; sub = info[2]; }
            canvas.drawText(fit(title, getWidth() - 24, textSize), 12, 23, textSize, ink());
            canvas.drawText(fit(detail, getWidth() - 24, 13), 12, 44, 13, accent());
            canvas.drawText(fit(sub, getWidth() - 24, 11), 12, 63, 11, muted());
        }
    }
    private final class Control extends Button {
        final String id, label;
        Control(Context context, String id, String label) { super(context); this.id = id; this.label = label; }
        @Override protected void onDraw(Canvas canvas) {
            boolean active = section.equals(id);
            canvas.fillRect(0, 0, getWidth(), getHeight(), isPressed() || active ? accent() : card());
            canvas.strokeRect(0, 0, getWidth(), getHeight(), dark() ? 0xff41526d : 0xffcbd5e5);
            String text = id.equals("theme") ? dark() ? "Dark" : "Light" : id.equals("font") ? textSize + " px" : id.equals("clock") ? clock24 ? "24-hour" : "12-hour" : label;
            text = fit(text, getWidth() - 8, 13);
            int width = (int) com.android.graphics.FontManager.measureText(text, 13);
            canvas.drawText(text, Math.max(4, (getWidth() - width) / 2), getHeight() / 2 + 5, 13, active || isPressed() ? dark() ? 0xff121d2e : 0xffffffff : ink());
        }
    }

}
