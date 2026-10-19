package com.packages.video;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.os.Handler;
import com.android.util.AttributeSet;
import com.android.view.*;
import com.android.widget.Button;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local video library, software picture surface and playback controls in the existing X11 window. */
public class VideoView extends ViewGroup {
    private final VideoEngine engine = new VideoEngine();
    private final Path home;
    private Path directory;
    private List<Path> files = new ArrayList<>();
    private final Map<String, Control> controls = new LinkedHashMap<>();
    private final FileRow[] rows = new FileRow[8];
    private final PathInput input;
    private final Picture picture;
    private final Timeline timeline;
    private int page, capacity = 5, inset = 24;
    private boolean busy, viewing, focused, disposed;
    private String status = "";
    private Timer timer;
    private final AtomicBoolean refreshPending = new AtomicBoolean();
    private final Handler ui = new Handler(message -> {
        ((Runnable) message.obj).run(); return true;
    });
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> { Thread worker = new Thread(r, "video-library"); worker.setDaemon(true); return worker; });
    private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);
    private static final int INK = 0xffe8f0ff, MUTED = 0xff9cafca, ACCENT = 0xff83b6ff;

    public VideoView(Context context, AttributeSet attrs) {
        super(context, attrs); mBackground = 0xff101b2d;
        home = Paths.get(System.getProperty("framework.video.directory", Paths.get(System.getProperty("user.home"), "Videos").toString())).toAbsolutePath().normalize(); directory = home;
        control("open", "Open", this::openInput);
        control("home", "Videos", () -> browse(home)); control("up", "Up", () -> { if (directory.getParent() != null) browse(directory.getParent()); }); control("refresh", "Refresh", () -> browse(directory));
        control("previous", "< Previous", () -> { if (page > 0) { page--; invalidate(); } }); control("next", "Next >", () -> { if ((page + 1) * capacity < files.size()) { page++; invalidate(); } });
        control("play", "Play", () -> { engine.togglePause(); focused = false; invalidate(); });
        control("stop", "Stop", () -> { engine.stop(); invalidate(); });
        control("rewind", "-10 sec", () -> seekBy(-10)); control("forward", "+10 sec", () -> seekBy(10));
        control("library", "Library", () -> { engine.stop(); viewing = false; focused = false; browse(directory); });
        control("mute", "Mute", () -> { engine.setMuted(!engine.isMuted()); invalidate(); });
        control("volume_down", "Volume -", () -> { engine.setVolume(engine.getVolume() - 10); invalidate(); }); control("volume_up", "Volume +", () -> { engine.setVolume(engine.getVolume() + 10); invalidate(); });
        input = new PathInput(context); input.setId("path"); input.setOnClickListener(v -> { if (!busy) { focused = true; input.cursor = input.text.length(); input.all = false; invalidate(); } }); addView(input, new LayoutParams(-2, -2));
        picture = new Picture(context); picture.setId("picture"); addView(picture, new LayoutParams(-2, -2));
        timeline = new Timeline(context); timeline.setId("timeline"); addView(timeline, new LayoutParams(-2, -2));
        for (int i = 0; i < rows.length; i++) { rows[i] = new FileRow(context, i); rows[i].setId("row_" + i); addView(rows[i], new LayoutParams(-2, -2)); }
        String startup = System.getProperty("framework.video.file");
        if (startup != null) { input.set(startup); openInput(); } else browse(home);
    }
    private void control(String id, String text, Runnable action) {
        Control button = new Control(getContext(), text); button.setId(id); button.setOnClickListener(v -> { if (!busy) action.run(); });
        controls.put(id, button); addView(button, new LayoutParams(-2, -2));
    }
    private interface Work { Runnable run() throws Exception; }
    private void work(String description, Work task) {
        if (busy || disposed) return;
        busy = true; status = description; invalidate();
        io.execute(() -> {
            Runnable done;
            try { done = task.run(); }
            catch (Exception e) { String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); done = () -> status = "Error: " + error; }
            final Runnable action = done;
            ui.sendMessage(ui.obtainMessage(0, (Runnable) () -> { busy = false; if (!disposed) { action.run(); requestLayout(); invalidate(); } }));
        });
    }
    private void openInput() {
        try { open(directory.resolve(input.text.trim())); }
        catch (InvalidPathException e) { status = "Invalid file path"; invalidate(); }
    }
    private void open(Path path) {
        work("Opening...", () -> {
            Path absolute = path.toAbsolutePath().normalize();
            if (Files.isDirectory(absolute)) return scan(absolute);
            VideoEngine.Media media = VideoEngine.probe(absolute);
            return () -> { engine.open(media); directory = media.file.getParent(); input.set(media.file.toString()); viewing = true; focused = false; status = ""; };
        });
    }
    private void browse(Path path) { work("Reading video library...", () -> scan(path)); }
    private Runnable scan(Path path) throws Exception {
        Path absolute = path.toAbsolutePath().normalize(); List<Path> entries = new ArrayList<>(); boolean limited = false;
        if (Files.exists(absolute)) {
            if (!Files.isDirectory(absolute)) throw new java.io.IOException("Choose a video directory");
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(absolute)) {
                for (Path entry : stream) {
                    if (entry.getFileName().toString().startsWith(".")) continue;
                    if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS) || (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) && entry.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mkv|avi|mov|webm|m4v|mpg|mpeg|ts|3gp|ogv|wmv|flv)"))) {
                        if (entries.size() >= 2000) { limited = true; break; } entries.add(entry);
                    }
                }
            }
        }
        entries.sort(Comparator.comparing((Path entry) -> !Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)).thenComparing(entry -> entry.getFileName().toString().toLowerCase(Locale.ROOT)).thenComparing(Path::toString));
        final boolean truncated = limited;
        return () -> { engine.stop(); directory = absolute; input.set(directory.toString()); files = entries; page = 0; viewing = false; focused = false; status = truncated ? "First 2000 entries only" : entries.size() + " videos / folders"; };
    }
    private void seekBy(double seconds) { engine.seek(engine.getPosition() + seconds); focused = false; invalidate(); }
    public VideoEngine getEngine() { return engine; }
    public boolean isBusy() { return busy; }
    public boolean isViewing() { return viewing; }
    public String getStatus() { return status; }
    public int getFileCount() { return files.size(); }
    public String getDirectory() { return directory.toString(); }
    public String getRowName(int row) { int index = page * capacity + row; return index < files.size() ? files.get(index).getFileName().toString() : ""; }
    public void closePlayer() { disposed = true; io.shutdownNow(); engine.close(); if (timer != null) { timer.cancel(); timer = null; } }
    private boolean attached() { View root = this; while (root.getParent() instanceof View) root = (View) root.getParent(); return root.getParent() != null; }
    private void refresh() {
        if (timer != null) return;
        timer = new Timer("video-refresh", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                if (refreshPending.compareAndSet(false, true)) ui.sendMessage(ui.obtainMessage(0, (Runnable) () -> {
                    refreshPending.set(false); if (!attached()) closePlayer(); else if (viewing) invalidate();
                }));
            }
        }, 40, 40);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (busy) return true;
        if (event.getType() == KeyEvent.DOWN && event.getValue() == KeyEvent.ESCAPE) {
            focused = false; if (viewing) { engine.stop(); viewing = false; browse(directory); } invalidate(); return true;
        }
        if (focused) {
            if (event.getType() == KeyEvent.DOWN && event.getValue() == KeyEvent.ENTER) { openInput(); return true; }
            if (input.key(event)) { invalidate(); return true; } return false;
        }
        if (viewing) {
            if (event.getType() == KeyEvent.DOWN) {
                if (event.getValue() == KeyEvent.LEFT) { seekBy(-10); return true; }
                if (event.getValue() == KeyEvent.RIGHT) { seekBy(10); return true; }
                if (event.getValue() == KeyEvent.HOME) { engine.seek(0); return true; }
            } else if (event.getType() == KeyEvent.TEXT) {
                if (event.getValue() == ' ') { engine.togglePause(); invalidate(); return true; }
                if (event.getValue() == 'm' || event.getValue() == 'M') { engine.setMuted(!engine.isMuted()); invalidate(); return true; }
            }
        }
        return false;
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(600, widthSpec), resolveSize(800, heightSpec)); inset = Math.min(24, getMeasuredWidth() / 20);
        int previous = capacity; capacity = Math.max(1, Math.min(8, (getMeasuredHeight() - 282) / 76));
        if (previous != capacity) page = Math.min(page, Math.max(0, (files.size() - 1) / capacity));
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(40, MeasureSpec.EXACTLY));
    }
    private void place(View view, int x, int y, int width, int height) { view.layout(x, y, x + Math.max(0, width), y + Math.max(0, height)); }
    private void place(String id, int x, int y, int width, int height) { place(controls.get(id), x, y, width, height); }
    private int playerBottom() { return getHeight() - (getHeight() < 600 ? 260 : 300); }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) place(getChildAt(i), 0, 0, 0, 0);
        int width = getWidth() - inset * 2, third = (width - 16) / 3;
        place(input, inset, 90, width - 68, 40); place("open", getWidth() - inset - 60, 90, 60, 40);
        if (!viewing) {
            String[] tools = {"home", "up", "refresh"}; for (int i = 0; i < 3; i++) place(tools[i], inset + i * (third + 8), 142, third, 36);
            for (int i = 0; i < capacity; i++) place(rows[i], inset, 194 + i * 76, width, 68);
            place("previous", inset, getHeight() - 76, 100, 38); place("next", getWidth() - inset - 100, getHeight() - 76, 100, 38);
        } else {
            int bottom = playerBottom(); place(picture, inset, 142, width, Math.max(1, bottom - 142)); place(timeline, inset, bottom + 25, width, 22);
            String[] primary = {"rewind", "play", "forward"}, secondary = {"stop", "mute", "library"};
            for (int i = 0; i < 3; i++) { place(primary[i], inset + i * (third + 8), bottom + 60, third, 36); place(secondary[i], inset + i * (third + 8), bottom + 106, third, 36); }
            place("volume_down", inset, bottom + 152, 90, 32); place("volume_up", getWidth() - inset - 90, bottom + 152, 90, 32);
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        if (!disposed && attached() && timer == null) refresh();
        canvas.drawText("VIDEO", inset, 39, 25, INK); canvas.drawText("Local video player", inset, 65, 13, MUTED);
        int width = getWidth() - inset * 2;
        if (viewing) {
            VideoEngine.Media media = engine.getMedia();
            canvas.drawText(time(engine.getPosition()), inset, playerBottom() + 17, 12, MUTED);
            canvas.drawText(time(media == null ? 0 : media.duration), getWidth() - inset - 62, playerBottom() + 17, 12, MUTED);
            canvas.drawText((engine.isMuted() ? "Muted / " : "") + engine.getVolume() + "%", getWidth() / 2 - 37, playerBottom() + 174, Math.min(13, getWidth() / 27), MUTED);
            String detail = media == null ? "" : media.width + " x " + media.height + " / " + media.file.getFileName();
            canvas.drawText(fit(detail, width, 12), inset, getHeight() - 55, 12, MUTED);
            String message = !engine.getError().isEmpty() ? "Error: " + engine.getError() : !status.isEmpty() ? status : engine.getState();
            canvas.drawText(fit(message, width, 12), inset, getHeight() - 35, 12, engine.getState().equals("Error") ? 0xffffb4a9 : ACCENT);
            canvas.drawText(fit(engine.getAudioStatus(), width, 11), inset, getHeight() - 15, 11, MUTED);
        } else {
            if (files.isEmpty()) { canvas.drawText("No videos in this folder", inset, 235, Math.min(21, getWidth() / 16), INK); canvas.drawText("Enter a local file or folder path above.", inset, 267, Math.min(14, getWidth() / 27), MUTED); }
            canvas.drawText((page + 1) + " / " + Math.max(1, (files.size() + capacity - 1) / capacity), getWidth() / 2 - 18, getHeight() - 50, 14, MUTED);
            canvas.drawText(fit(status, width, 12), inset, getHeight() - 16, 12, ACCENT);
        }
    }
    private static String time(double seconds) { long value = Math.max(0, (long) seconds); return String.format(Locale.ROOT, "%02d:%02d:%02d", value / 3600, value / 60 % 60, value % 60); }
    private static String fit(String text, int width, int size) {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, size); int end = text.length();
        while (end > 0 && font.getStringBounds(text.substring(0, end) + (end < text.length() ? "..." : ""), METRICS).getWidth() > width) end = text.offsetByCodePoints(end, -1);
        return text.substring(0, end) + (end < text.length() ? "..." : "");
    }
    private final class FileRow extends View {
        final int row;
        FileRow(Context context, int row) { super(context); this.row = row; setOnClickListener(v -> { int index = page * capacity + row; if (!busy && index < files.size()) open(files.get(index)); }); }
        @Override protected void onDraw(Canvas canvas) {
            int index = page * capacity + row; if (index >= files.size()) return;
            Path file = files.get(index); boolean folder = Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS);
            canvas.fillRect(0, 0, getWidth(), getHeight(), isPressed() ? 0xff2d466a : 0xff1c2b42);
            canvas.drawText(fit(file.getFileName().toString(), getWidth() - 24, 16), 12, 26, 16, INK);
            canvas.drawText(folder ? "Folder >" : "Video / click to play", 12, 51, 12, ACCENT);
        }
    }
    private final class Picture extends View {
        Picture(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            canvas.fillRect(0, 0, getWidth(), getHeight(), 0xff050a12);
            java.awt.image.BufferedImage frame = engine.getFrame();
            if (frame == null) { canvas.drawText(engine.getState().equals("Opening") ? "Opening video..." : "Press Play", 12, Math.max(22, getHeight()/2), 17, MUTED); return; }
            double scale = Math.min((double) getWidth() / frame.getWidth(), (double) getHeight() / frame.getHeight()); int w = (int) (frame.getWidth() * scale), h = (int) (frame.getHeight() * scale);
            canvas.drawImage(frame, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
        }
    }
    private final class Timeline extends View {
        boolean dragging; double fraction;
        Timeline(Context context) { super(context); }
        @Override protected boolean onTouchEvent(MotionEvent event) {
            VideoEngine.Media media = engine.getMedia(); if (media == null || media.duration <= 0) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) dragging = true;
            if (!dragging) return false;
            fraction = Math.max(0, Math.min(1, (double) event.getX() / Math.max(1, getWidth())));
            if (event.getAction() == MotionEvent.ACTION_UP) { dragging = false; engine.seek(fraction * media.duration); focused = false; }
            if (event.getAction() == MotionEvent.ACTION_CANCEL) dragging = false;
            invalidate(); return true;
        }
        @Override protected void onDraw(Canvas canvas) {
            VideoEngine.Media media = engine.getMedia(); double value = dragging ? fraction : media == null || media.duration <= 0 ? 0 : Math.min(1, engine.getPosition() / media.duration);
            canvas.fillRect(0, 8, getWidth(), 5, 0xff334a6a); canvas.fillRect(0, 8, (int) (getWidth() * value), 5, ACCENT); canvas.fillRect(Math.min(getWidth() - 5, (int) (getWidth() * value)), 3, 5, 15, INK);
        }
    }
    private final class Control extends Button {
        final String label;
        Control(Context context, String label) { super(context); this.label = label; }
        @Override protected void onDraw(Canvas canvas) {
            canvas.fillRect(0, 0, getWidth(), getHeight(), isPressed() ? 0xff345581 : 0xff213653); canvas.strokeRect(0, 0, getWidth(), getHeight(), 0xff47658c);
            String text = getId().equals("play") ? engine.getState().equals("Playing") || engine.getState().equals("Opening") ? "Pause" : "Play" : getId().equals("mute") && engine.isMuted() ? "Unmute" : label;
            text = fit(text, getWidth() - 8, 13); int width = (int) new Font(Font.SANS_SERIF, Font.PLAIN, 13).getStringBounds(text, METRICS).getWidth(); canvas.drawText(text, Math.max(4, (getWidth() - width)/2), getHeight()/2 + 5, 13, INK);
        }
    }
    private final class PathInput extends View {
        String text = ""; int cursor; boolean all;
        PathInput(Context context) { super(context); }
        void set(String value) { text = value; cursor = value.length(); all = false; }
        boolean key(KeyEvent event) {
            int key = event.getValue();
            if (event.getType() == KeyEvent.TEXT) {
                if (!Character.isValidCodePoint(key) || Character.isISOControl(key)) return false;
                if ((all ? 0 : text.length()) >= 4096) return true;
                if (all) set(""); String insert = new String(Character.toChars(key)); text = text.substring(0, cursor) + insert + text.substring(cursor); cursor += insert.length(); return true;
            }
            if (event.getType() != KeyEvent.DOWN) return false;
            if ((event.getModifiers() & KeyEvent.CONTROL) != 0 && (key == 'a' || key == 'A')) { all = true; return true; }
            if (key == KeyEvent.HOME) cursor = 0;
            else if (key == KeyEvent.END) cursor = text.length();
            else if (key == KeyEvent.LEFT) { if (cursor > 0) cursor = text.offsetByCodePoints(cursor, -1); }
            else if (key == KeyEvent.RIGHT) { if (cursor < text.length()) cursor = text.offsetByCodePoints(cursor, 1); }
            else if (key == KeyEvent.BACKSPACE || key == KeyEvent.DELETE) {
                if (all) set("");
                else if (key == KeyEvent.BACKSPACE && cursor > 0) { int before = text.offsetByCodePoints(cursor, -1); text = text.substring(0, before) + text.substring(cursor); cursor = before; }
                else if (key == KeyEvent.DELETE && cursor < text.length()) text = text.substring(0, cursor) + text.substring(text.offsetByCodePoints(cursor, 1));
            } else return false;
            all = false; return true;
        }
        @Override protected void onDraw(Canvas canvas) {
            canvas.fillRect(0, 0, getWidth(), getHeight(), focused && all ? 0xff2e4d7a : 0xff1b2b43); canvas.strokeRect(0, 0, getWidth(), getHeight(), focused ? ACCENT : 0xff47658c);
            Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 13); int start = 0;
            while (start < cursor && font.getStringBounds(text.substring(start, cursor), METRICS).getWidth() > getWidth() - 28) start = text.offsetByCodePoints(start, 1);
            canvas.save(); try { canvas.clipRect(8, 4, Math.max(0, getWidth() - 16), getHeight() - 8); canvas.drawText(text.substring(start), 10, 26, 13, INK); if (focused) canvas.fillRect(10 + (int) font.getStringBounds(text.substring(start, cursor), METRICS).getWidth(), 8, 1, 24, ACCENT); } finally { canvas.restore(); }
        }
    }
}
