package com.packages.gallery;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.os.Handler;
import com.android.util.AttributeSet;
import com.android.view.*;
import com.android.widget.EditText;
import com.android.widget.Button;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/** Paged thumbnail album and a zoomable, draggable single-image viewer. */
public class GalleryView extends ViewGroup {
    private final Path home, camera;
    private Path directory;
    private List<GalleryImages.Photo> photos = new ArrayList<>();
    private final Map<Path, GalleryImages.Decoded> thumbnails = new LinkedHashMap<Path, GalleryImages.Decoded>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Path, GalleryImages.Decoded> entry) { return size() > 32; }
    };
    private final Set<Path> broken = new HashSet<>();
    private final Map<String, Button> buttons = new LinkedHashMap<>();
    private final Tile[] tiles = new Tile[16];
    private final EditText input;
    private final PhotoSurface surface;
    private GalleryImages.Decoded current;
    private boolean busy, viewing;
    private String status = "";
    private int index, page, columns = 3, rows = 3, capacity = 9, inset;
    private double zoom = 1, panX, panY;
    private final Handler ui = new Handler(message -> { ((Runnable) message.obj).run(); return true; });
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread worker = new Thread(r, "gallery-images"); worker.setDaemon(true); return worker;
    });
    private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);

    public GalleryView(Context context, AttributeSet attrs) {
        super(context, attrs); mBackground = 0xfff5f7fc;
        home = Paths.get(System.getProperty("framework.gallery.directory", Paths.get(System.getProperty("user.home"), "Pictures").toString())).toAbsolutePath().normalize();
        camera = Paths.get(System.getProperty("framework.camera.output", Paths.get(System.getProperty("user.home"), "Pictures", "FrameworkCamera").toString())).toAbsolutePath().normalize();
        directory = home;
        input = new EditText(context); input.setId("path"); input.setSingleLine(); input.setMaxLength(4096); input.setTextSize(13); input.setTextColor(0xff344054);
        addView(input, new LayoutParams(-2, -2));
        button("go", "Go", this::go);
        button("home", "Pictures", () -> scan(home)); button("camera", "Camera photos", () -> scan(camera)); button("refresh", "Refresh", () -> scan(directory));
        button("previous", "< Previous", () -> { if (viewing) { if (index > 0) open(index - 1); } else if (page > 0) { page--; invalidate(); } });
        button("next", "Next >", () -> { if (viewing) { if (index + 1 < photos.size()) open(index + 1); } else if ((page + 1) * capacity < photos.size()) { page++; invalidate(); } });
        button("back", "Grid", this::back);
        button("zoom_in", "+", () -> setZoom(zoom * 1.5)); button("zoom_out", "-", () -> setZoom(zoom / 1.5));
        button("fit", "Fit", () -> { zoom = 1; panX = panY = 0; invalidate(); });
        surface = new PhotoSurface(context); surface.setId("photo"); addView(surface, new LayoutParams(-2, -2));
        for (int i = 0; i < tiles.length; i++) {
            final int position = i;
            Tile tile = new Tile(context, i); tiles[i] = tile; tile.setId("tile_" + i);
            tile.setOnClickListener(view -> { int target = page * capacity + position; if (!busy && target < photos.size()) open(target); });
            addView(tile, new LayoutParams(-2, -2));
        }
        setInput(input, directory.toString()); scan(directory);
    }
    private void button(String id, String text, Runnable action) {
        Button button = new GalleryButton(getContext()); button.setId(id); button.setText(text);
        button.setOnClickListener(view -> { if (!busy) action.run(); });
        buttons.put(id, button); addView(button, new LayoutParams(-2, -2));
    }
    private interface Work { Runnable execute() throws Exception; }
    private void work(String message, Work task) {
        if (busy) return;
        busy = true; status = message; invalidate();
        io.execute(() -> {
            Runnable result;
            try { result = task.execute(); }
            catch (Exception e) { String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); result = () -> status = "Failed: " + detail; }
            final Runnable done = result;
            ui.sendMessage(ui.obtainMessage(0, (Runnable) () -> { busy = false; done.run(); requestLayout(); invalidate(); }));
        });
    }
    private void go() {
        try { scan(directory.resolve(input.getText())); }
        catch (InvalidPathException e) { status = "Invalid directory path"; invalidate(); }
    }
    private void scan(Path path) {
        work("Scanning pictures...", () -> {
            GalleryImages.Album album = GalleryImages.scan(path);
            return () -> {
                photos = album.photos; index = 0; directory = album.directory; setInput(input, directory.toString());
                thumbnails.clear(); broken.clear(); current = null; viewing = false; page = 0; clearFocus();
                status = (album.limited ? "First 3000 photos only. " : "") + (album.skipped > 0 ? album.skipped + " unreadable entries skipped" : "");
            };
        });
    }
    private void loadThumbnails() {
        if (busy || viewing || photos.isEmpty()) return;
        List<GalleryImages.Photo> missing = new ArrayList<>();
        for (int i = page * capacity; i < Math.min(photos.size(), (page + 1) * capacity); i++) {
            GalleryImages.Photo photo = photos.get(i);
            if (!thumbnails.containsKey(photo.path) && !broken.contains(photo.path)) missing.add(photo);
        }
        if (missing.isEmpty()) return;
        work("Loading thumbnails...", () -> {
            Map<Path, GalleryImages.Decoded> loaded = new LinkedHashMap<>(); Set<Path> failures = new HashSet<>();
            for (GalleryImages.Photo photo : missing) {
                try { loaded.put(photo.path, GalleryImages.decode(photo.path, 256)); }
                catch (Exception e) { failures.add(photo.path); }
            }
            return () -> { thumbnails.putAll(loaded); broken.addAll(failures); status = failures.isEmpty() ? "" : "Some images could not be loaded"; };
        });
    }
    private void open(int target) {
        if (target < 0 || target >= photos.size()) return;
        final GalleryImages.Photo photo = photos.get(target);
        work("Opening photo...", () -> {
            GalleryImages.Decoded decoded = null; String error = "";
            try { decoded = GalleryImages.decode(photo.path, 2048); }
            catch (Exception e) { error = "Cannot open image: " + (e.getMessage() == null ? "Damaged image" : e.getMessage()); }
            final GalleryImages.Decoded image = decoded; final String result = error;
            return () -> { index = target; current = image; viewing = true; zoom = 1; panX = panY = 0; clearFocus(); status = result; };
        });
    }
    private void back() { viewing = false; current = null; page = index / capacity; status = ""; requestLayout(); invalidate(); }
    private void setZoom(double value) { if (current != null) { zoom = Math.max(.25, Math.min(4, value)); invalidate(); } }
    public boolean isBusy() { return busy; }
    public boolean isViewing() { return viewing; }
    public int getPhotoCount() { return photos.size(); }
    public String getDirectory() { return directory.toString(); }
    public String getStatus() { return status; }
    public String getCurrentName() { return photos.isEmpty() ? "" : photos.get(index).label; }
    public String getTileName(int tile) { int i = page * capacity + tile; return i < photos.size() ? photos.get(i).label : ""; }
    public double getZoom() { return zoom; }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (busy && event.getAction() != MotionEvent.ACTION_CANCEL) return true;
        return super.dispatchTouchEvent(event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (busy) return true;
        if (event.getType() == KeyEvent.DOWN && (event.getValue() == KeyEvent.TAB || event.getValue() == KeyEvent.ISO_LEFT_TAB)) {
            if (input.getWidth() > 0) input.requestFocus();
            return true;
        }
        if (event.getType() == KeyEvent.DOWN) {
            int key = event.getValue();
            if (viewing) {
                if (key == KeyEvent.ESCAPE) { back(); return true; }
                if (key == KeyEvent.LEFT) { if (index > 0) open(index - 1); return true; }
                if (key == KeyEvent.RIGHT) { if (index + 1 < photos.size()) open(index + 1); return true; }
            } else if (key == KeyEvent.ENTER || key == 0xff8d) { if (input.isFocused()) go(); return true; }
        }
        if (viewing && event.getType() == KeyEvent.TEXT) {
            if (event.getValue() == '+' || event.getValue() == '=') { setZoom(zoom * 1.5); return true; }
            if (event.getValue() == '-') { setZoom(zoom / 1.5); return true; }
            if (event.getValue() == '0') { zoom = 1; panX = panY = 0; invalidate(); return true; }
        }
        if (!viewing && input.isFocused() && input.dispatchKeyEvent(event)) { invalidate(); return true; }
        return false;
    }
    private static void setInput(EditText field, String value) {
        field.setText(value);
        field.setSelection(field.getText().length());
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(600, widthSpec), resolveSize(800, heightSpec));
        inset = Math.min(24, getMeasuredWidth() / 20);
        int old = capacity;
        columns = Math.max(2, Math.min(4, getMeasuredWidth() / 160));
        rows = Math.max(1, Math.min(4, (getMeasuredHeight() - 280) / 160));
        capacity = columns * rows;
        if (old != capacity) page = Math.min(page, Math.max(0, (photos.size() - 1) / capacity));
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(
                MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(40, MeasureSpec.EXACTLY));
    }
    private void place(View view, int x, int y, int width, int height) { view.layout(x, y, x + Math.max(0, width), y + Math.max(0, height)); }
    private void control(String id, int x, int y, int width, int height) { place(buttons.get(id), x, y, width, height); }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) place(getChildAt(i), 0, 0, 0, 0);
        int width = getWidth() - 2 * inset, height = getHeight();
        if (!viewing) {
            place(input, inset, 66, width - 58, 40); control("go", getWidth() - inset - 50, 66, 50, 40);
            String[] tools = {"home", "camera", "refresh"}; int col = (width - 16) / 3;
            for (int i = 0; i < tools.length; i++) control(tools[i], inset + i * (col + 8), 118, col, 38);
            for (int i = 0; i < capacity; i++) {
                int x = inset + (i % columns) * (width + 10) / columns;
                int right = inset + (i % columns + 1) * (width + 10) / columns - 10;
                int y = 196 + (i / columns) * (height - 280) / rows;
                int bottom = 196 + (i / columns + 1) * (height - 280) / rows - 10;
                place(tiles[i], x, y, right - x, bottom - y);
            }
            control("previous", inset, height - 62, 100, 40); control("next", getWidth() - inset - 100, height - 62, 100, 40);
        } else {
            place(surface, inset, 110, width, Math.max(0, height - 250));
            String[] tools = {"zoom_out", "fit", "zoom_in"}; int col = (width - 16) / 3;
            for (int i = 0; i < 3; i++) control(tools[i], inset + i * (col + 8), height - 118, col, 36);
            String[] nav = {"previous", "back", "next"};
            for (int i = 0; i < 3; i++) control(nav[i], inset + i * (col + 8), height - 62, col, 40);
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawText("GALLERY", inset, 39, 24, 0xff182230);
        if (!viewing) {
            canvas.drawText(photos.size() + " photos", inset, 181, 13, 0xff667085);
            if (photos.isEmpty()) {
                canvas.drawText(busy ? "Loading photos..." : "No photos in this folder", inset, 244, 20, 0xff667085);
                canvas.drawText("Choose a folder or open Camera photos", inset, 276, Math.min(14, getWidth() / 29), 0xff667085);
            }
            int pages = Math.max(1, (photos.size() + capacity - 1) / capacity);
            canvas.drawText((page + 1) + " / " + pages, getWidth() / 2 - 20, getHeight() - 36, 15, 0xff667085);
            loadThumbnails();
        } else {
            canvas.drawText(fit(getCurrentName(), getWidth() - 2 * inset, 17), inset, 70, 17, 0xff344054);
            String details = (index + 1) + " / " + photos.size() + (current == null ? "" : "   " + current.width + " x " + current.height + "   Zoom " + Math.round(zoom * 100) + "%");
            canvas.drawText(details, inset, 94, Math.min(14, getWidth() / 29), 0xff667085);
        }
        canvas.drawText(fit(status, getWidth() - 2 * inset, 12), inset, getHeight() - (viewing ? 129 : 82), 12, 0xff39716a);
    }
    private static String fit(String value, int width, int size) {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, size);
        if (font.getStringBounds(value, METRICS).getWidth() <= width) return value;
        int end = value.length();
        while (end > 0 && font.getStringBounds(value.substring(0, end) + "...", METRICS).getWidth() > width) end = value.offsetByCodePoints(end, -1);
        return value.substring(0, end) + "...";
    }
    private final class Tile extends View {
        final int position;
        Tile(Context context, int position) { super(context); this.position = position; }
        @Override protected void onDraw(Canvas canvas) {
            int i = page * capacity + position; if (i >= photos.size()) return;
            GalleryImages.Photo photo = photos.get(i);
            canvas.fillRect(0, 0, getWidth(), getHeight(), isPressed() ? 0xffdce7ff : 0xffffffff);
            canvas.strokeRect(0, 0, getWidth(), getHeight(), 0xffdce3ee);
            GalleryImages.Decoded thumbnail = thumbnails.get(photo.path);
            int available = Math.max(0, getHeight() - 34);
            if (thumbnail != null) {
                double scale = Math.min((double) (getWidth() - 12) / thumbnail.image.getWidth(), (double) (available - 12) / thumbnail.image.getHeight());
                int w = (int) (thumbnail.image.getWidth() * scale), h = (int) (thumbnail.image.getHeight() * scale);
                canvas.drawImage(thumbnail.image, (getWidth() - w) / 2, (available - h) / 2, w, h);
            } else canvas.drawText(broken.contains(photo.path) ? "Unavailable" : "Loading...", 10, Math.max(25, available / 2), 12, 0xff98a2b3);
            canvas.drawText(fit(photo.label, getWidth() - 16, 12), 8, getHeight() - 12, 12, 0xff344054);
        }
    }
    private final class PhotoSurface extends View {
        int lastX, lastY; boolean dragging;
        PhotoSurface(Context context) { super(context); }
        @Override protected boolean onTouchEvent(MotionEvent event) {
            if (current == null || busy) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) { dragging = true; lastX = event.getX(); lastY = event.getY(); return true; }
            if (event.getAction() == MotionEvent.ACTION_MOVE && dragging) {
                panX += event.getX() - lastX; panY += event.getY() - lastY; lastX = event.getX(); lastY = event.getY(); invalidate(); return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) { dragging = false; return true; }
            return dragging;
        }
        @Override protected void onDraw(Canvas canvas) {
            canvas.fillRect(0, 0, getWidth(), getHeight(), 0xff101824);
            if (current == null) { canvas.drawText("Image unavailable", 16, 40, 18, 0xffb6c7e0); return; }
            double scale = Math.min((double) getWidth() / current.image.getWidth(), (double) getHeight() / current.image.getHeight()) * zoom;
            int width = (int) (current.image.getWidth() * scale), height = (int) (current.image.getHeight() * scale);
            double maxX = Math.max(0, (width - getWidth()) / 2.0), maxY = Math.max(0, (height - getHeight()) / 2.0);
            panX = Math.max(-maxX, Math.min(maxX, panX)); panY = Math.max(-maxY, Math.min(maxY, panY));
            canvas.drawImage(current.image, (getWidth() - width) / 2 + (int) panX, (getHeight() - height) / 2 + (int) panY, width, height);
        }
    }
    private static final class GalleryButton extends Button {
        GalleryButton(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            if (isPressed()) canvas.fillRect(1, 1, getWidth() - 2, getHeight() - 2, 0xffc9d8ec);
            canvas.strokeRect(0, 0, getWidth(), getHeight(), 0xff8c99a8);
            Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
            int width = (int) font.getStringBounds(getText(), METRICS).getWidth();
            canvas.drawText(getText(), Math.max(4, (getWidth() - width) / 2), (getHeight() - 14) / 2 + 13, 14, 0xff202124);
        }
    }

}
