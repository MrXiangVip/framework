package com.packages.filebrowser;

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

/** File manager controls and bounded previews, drawn through the framework's View tree. */
public class FileBrowserView extends ViewGroup {
    private final Path home;
    private Path directory, selected, clipboard;
    private boolean cut, hidden, busy;
    private String status = "", prompt = "";
    private String mode = "browse";
    private final Input path, search, name;
    private Input focused;
    private final Map<String, Button> controls = new LinkedHashMap<>();
    private final Row[] rows = new Row[10];
    private List<FileOperations.Entry> entries = new ArrayList<>(), filtered = new ArrayList<>();
    private FileOperations.Preview preview;
    private List<String> previewLines = new ArrayList<>();
    private int page, pageSize = 6, inset;
    private final Handler ui = new Handler(message -> { ((Runnable) message.obj).run(); return true; });
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "filebrowser-io"); thread.setDaemon(true); return thread;
    });
    private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);

    public FileBrowserView(Context context, AttributeSet attrs) {
        super(context, attrs); mBackground = 0xfff5f7fc;
        home = Paths.get(System.getProperty("framework.filebrowser.home", System.getProperty("user.home"))).toAbsolutePath().normalize();
        directory = home;
        path = input("path", "Directory path", 4096); search = input("search", "Search files in this folder", 120); name = input("name", "Name", 255);
        focused = search;
        button("home", "Home", () -> navigate(home));
        button("up", "Up", () -> { Path parent = directory.getParent(); if (parent != null) navigate(parent); });
        button("open", "Open", this::openSelected);
        button("refresh", "Refresh", () -> navigate(directory));
        button("go", "Go", this::navigateInput);
        button("hidden", "Hidden", () -> { hidden = !hidden; navigate(directory); });
        button("new", "New folder", () -> showPrompt("new", "Create folder", ""));
        button("rename", "Rename", () -> { if (requireSelection()) showPrompt("rename", "Rename: " + selected.getFileName(), selected.getFileName().toString()); });
        button("delete", "Delete", () -> { if (requireSelection()) { mode = "delete"; prompt = "Delete: " + selected.getFileName(); status = "This also deletes all contents of a folder."; requestLayout(); invalidate(); } });
        button("copy", "Copy", () -> copySelection(false)); button("cut", "Move", () -> copySelection(true));
        button("paste", "Paste", this::paste);
        button("previous", "<", () -> { if (page > 0) { page--; selected = null; status = ""; } invalidate(); });
        button("next", ">", () -> { if ((page + 1) * pageSize < pageCountItems()) { page++; if (mode.equals("browse")) selected = null; status = ""; } invalidate(); });
        button("confirm", "Confirm", this::confirm);
        button("cancel", "Cancel", () -> { mode = "browse"; focused = search; status = ""; requestLayout(); invalidate(); });
        button("back", "Back", () -> { mode = "browse"; preview = null; page = 0; focused = search; requestLayout(); invalidate(); });
        for (int i = 0; i < rows.length; i++) {
            Row row = new Row(context, i); rows[i] = row; row.setId("row_" + i);
            row.setOnClickListener(view -> {
                if (busy) return;
                FileOperations.Entry entry = row.entry();
                if (entry != null) {
                    if (entry.path.equals(selected) && entry.directory) navigate(entry.path);
                    else { selected = entry.path; status = entry.name + "  |  " + (entry.directory ? "Folder" : size(entry.size)) + (entry.link ? "  |  Link" : ""); invalidate(); }
                }
            });
            addView(row, new LayoutParams(-2, -2));
        }
        path.set(directory.toString()); navigate(directory);
    }
    private Input input(String id, String hint, int limit) {
        Input value = new Input(getContext(), hint, limit); value.setId(id);
        value.setOnClickListener(view -> { if (!busy) { focused = value; value.cursor = value.text.length(); value.all = false; invalidate(); } });
        addView(value, new LayoutParams(-2, -2)); return value;
    }
    private void button(String id, String text, Runnable action) {
        Button button = new ToolButton(getContext()); button.setId(id); button.setText(text);
        button.setOnClickListener(view -> { if (!busy) action.run(); });
        controls.put(id, button); addView(button, new LayoutParams(-2, -2));
    }
    private interface Work { Runnable execute() throws Exception; }
    private void work(String description, Work work) {
        if (busy) return;
        busy = true; status = description; invalidate();
        io.execute(() -> {
            Runnable done;
            try { done = work.execute(); }
            catch (Exception e) {
                String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                done = () -> status = "Failed: " + detail;
            }
            final Runnable result = done;
            ui.sendMessage(ui.obtainMessage(0, (Runnable) () -> {
                busy = false; result.run(); requestLayout(); invalidate();
            }));
        });
    }
    private void navigateInput() {
        try { navigate(directory.resolve(path.text)); }
        catch (InvalidPathException e) { status = "Invalid path"; invalidate(); }
    }
    private void navigate(Path target) {
        final boolean showHidden = hidden;
        work("Loading folder...", () -> {
            FileOperations.Listing listing = FileOperations.list(target, showHidden);
            return () -> applyListing(listing, listing.limited ? "Showing first 10000 entries" : "");
        });
    }
    private void applyListing(FileOperations.Listing listing, String message) {
        directory = listing.directory; entries = listing.entries; selected = null;
        path.set(directory.toString()); search.set(""); page = 0; filter(); mode = "browse"; focused = search; preview = null;
        status = message;
    }
    private void filter() {
        filtered = new ArrayList<>(); String query = search.text.toLowerCase(Locale.ROOT);
        for (FileOperations.Entry entry : entries) if (entry.name.toLowerCase(Locale.ROOT).contains(query)) filtered.add(entry);
        page = Math.min(page, Math.max(0, (filtered.size() - 1) / pageSize));
        selected = null;
    }
    private boolean requireSelection() {
        if (selected != null) return true;
        status = "Select a file or folder first"; invalidate(); return false;
    }
    private void openSelected() {
        if (!requireSelection()) return;
        Path target = selected;
        work("Opening...", () -> {
            if (Files.isDirectory(target)) {
                FileOperations.Listing listing = FileOperations.list(target, hidden);
                return () -> applyListing(listing, listing.limited ? "Showing first 10000 entries" : "");
            }
            FileOperations.Preview value = FileOperations.preview(target);
            return () -> { preview = value; mode = "preview"; page = 0; status = value.limited ? "Preview limited to 32768 characters" : "Read-only preview"; };
        });
    }
    private void showPrompt(String operation, String title, String initial) {
        mode = operation; prompt = title; name.set(initial); focused = name; status = ""; requestLayout(); invalidate();
    }
    private void copySelection(boolean move) {
        if (!requireSelection()) return;
        clipboard = selected; cut = move; status = (move ? "Move: " : "Copy: ") + selected.getFileName() + "; open destination and click Paste"; invalidate();
    }
    private void paste() {
        if (clipboard == null) { status = "Copy or Move an item first"; invalidate(); return; }
        final Path source = clipboard, destination = directory; final boolean move = cut, showHidden = hidden;
        work(move ? "Moving..." : "Copying...", () -> {
            FileOperations.transfer(source, destination, move);
            FileOperations.Listing listing = FileOperations.list(destination, showHidden);
            return () -> { applyListing(listing, move ? "Moved" : "Copied"); if (move) clipboard = null; };
        });
    }
    private void confirm() {
        final String operation = mode, newName = name.text;
        final Path target = selected, folder = directory; final boolean showHidden = hidden;
        work("Working...", () -> {
            if (operation.equals("new")) FileOperations.createFolder(folder, newName);
            else if (operation.equals("rename")) FileOperations.rename(target, newName);
            else if (operation.equals("delete")) FileOperations.delete(target);
            else throw new IllegalStateException("No operation selected");
            FileOperations.Listing listing = FileOperations.list(folder, showHidden);
            return () -> applyListing(listing, operation.equals("delete") ? "Deleted" : "Done");
        });
    }
    public boolean isBusy() { return busy; }
    public String getDirectory() { return directory.toString(); }
    public String getStatus() { return status; }
    public String getMode() { return mode; }
    public int getEntryCount() { return filtered.size(); }
    public String getSelectedName() { return selected == null ? "" : selected.getFileName().toString(); }
    public String getRowName(int index) { FileOperations.Entry entry = rows[index].entry(); return entry == null ? "" : entry.name; }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (busy) return true;
        if (event.getType() == KeyEvent.DOWN) {
            if (event.getValue() == KeyEvent.ESCAPE && !mode.equals("browse")) { controls.get(mode.equals("preview") ? "back" : "cancel").performClick(); return true; }
            if (event.getValue() == KeyEvent.ENTER || event.getValue() == 0xff8d) {
                if (mode.equals("new") || mode.equals("rename")) confirm();
                else if (mode.equals("browse") && focused == path) { try { navigate(directory.resolve(path.text)); } catch (InvalidPathException e) { status = "Invalid path"; invalidate(); } }
                else if (mode.equals("browse") && selected != null) openSelected();
                return true;
            }
            if (event.getValue() == KeyEvent.TAB && mode.equals("browse")) { focused = focused == path ? search : path; invalidate(); return true; }
        }
        if (focused != null && !mode.equals("delete") && !mode.equals("preview") && focused.key(event)) {
            if (focused == search) { page = 0; filter(); } invalidate(); return true;
        }
        return false;
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(600, widthSpec), resolveSize(800, heightSpec));
        inset = Math.min(24, getMeasuredWidth() / 20);
        pageSize = mode.equals("preview") ? Math.max(1, (getMeasuredHeight() - 220) / 22)
                : Math.min(rows.length, Math.max(1, (getMeasuredHeight() - 418) / 54));
        if (mode.equals("preview") && preview != null && preview.text != null) wrapPreview(getMeasuredWidth() - 2 * inset - 16);
        page = Math.min(page, Math.max(0, (pageCountItems() - 1) / pageSize));
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(
                MeasureSpec.makeMeasureSpec(Math.max(0, getMeasuredWidth() - 2 * inset), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(40, MeasureSpec.EXACTLY));
    }
    private int pageCountItems() { return mode.equals("preview") ? previewLines.size() : filtered.size(); }
    private void place(View view, int x, int y, int width, int height) { view.layout(x, y, x + Math.max(0, width), y + Math.max(0, height)); }
    private void control(String id, int x, int y, int width, int height) { place(controls.get(id), x, y, width, height); }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) place(getChildAt(i), 0, 0, 0, 0);
        int width = getWidth() - 2 * inset, height = getHeight();
        if (mode.equals("browse")) {
            String[] nav = {"home", "up", "open", "refresh"}; int column = (width - 18) / 4;
            for (int i = 0; i < nav.length; i++) control(nav[i], inset + i * (column + 6), 58, column, 36);
            place(path, inset, 106, width - 56, 40); control("go", getWidth() - inset - 50, 106, 50, 40);
            place(search, inset, 158, width - 86, 40); control("hidden", getWidth() - inset - 80, 158, 80, 40);
            for (int i = 0; i < pageSize; i++) place(rows[i], inset, 222 + i * 54, width, 48);
            String[] tools = {"new", "rename", "delete", "copy", "cut", "paste"}; column = (width - 12) / 3;
            for (int i = 0; i < tools.length; i++) control(tools[i], inset + (i % 3) * (column + 6), height - 156 + (i / 3) * 44, column, 36);
        } else if (mode.equals("preview")) {
            control("back", inset, 58, 90, 38);
        } else {
            if (!mode.equals("delete")) place(name, inset, 150, width, 46);
            control("confirm", inset, height - 70, (width - 8) / 2, 46);
            control("cancel", inset + (width + 8) / 2, height - 70, (width - 8) / 2, 46);
        }
        if (mode.equals("browse") || (mode.equals("preview") && preview != null && preview.image == null)) {
            control("previous", inset, height - 60, 52, 36); control("next", getWidth() - inset - 52, height - 60, 52, 36);
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawText("FILE MANAGER", inset, 39, 23, 0xff182230);
        if (mode.equals("browse")) {
            canvas.drawText(filtered.size() + " items" + (hidden ? "  |  hidden shown" : ""), inset, 215, 12, 0xff667085);
            if (filtered.isEmpty()) canvas.drawText(busy ? "Loading..." : "No files in this view", inset, 255, 18, 0xff667085);
            drawStatus(canvas, getHeight() - 176);
        } else if (mode.equals("preview")) {
            canvas.drawText(fit(selected == null ? "" : selected.getFileName().toString(), getWidth() - 2 * inset - 108, 15), inset + 108, 84, 15, 0xff667085);
            if (preview.image != null) {
                int width = getWidth() - 2 * inset, height = Math.max(0, getHeight() - 180);
                double scale = Math.min((double) width / preview.image.getWidth(), (double) height / preview.image.getHeight());
                int w = (int) (preview.image.getWidth() * scale), h = (int) (preview.image.getHeight() * scale);
                canvas.drawImage(preview.image, (getWidth() - w) / 2, 110 + (height - h) / 2, w, h);
            } else {
                canvas.fillRect(inset, 110, getWidth() - 2 * inset, Math.max(0, getHeight() - 190), 0xffffffff);
                for (int i = 0; i < pageSize && page * pageSize + i < previewLines.size(); i++)
                    canvas.drawText(previewLines.get(page * pageSize + i), inset + 8, 131 + i * 22, 15, 0xff344054);
            }
            drawStatus(canvas, getHeight() - 75);
        } else {
            canvas.drawText(fit(prompt, getWidth() - 2 * inset, 18), inset, 108, 18, 0xff344054);
            if (mode.equals("delete")) canvas.drawText("Confirm permanently deleting this item?", inset, 150, Math.min(16, getWidth() / 23), 0xffb54747);
            drawStatus(canvas, getHeight() - 110);
        }
        if (mode.equals("browse") || (mode.equals("preview") && preview.image == null)) {
            int pages = Math.max(1, (pageCountItems() + pageSize - 1) / pageSize);
            canvas.drawText((page + 1) + " / " + pages, getWidth() / 2 - 20, getHeight() - 35, 15, 0xff667085);
        }
    }
    private void drawStatus(Canvas canvas, int y) { canvas.drawText(fit(status, getWidth() - 2 * inset, 12), inset, y, 12, busy ? 0xff5179db : 0xff39716a); }
    private void wrapPreview(int width) {
        previewLines = new ArrayList<>();
        for (String line : preview.text.replace("\t", "    ").split("\\r?\\n", -1)) {
            if (line.isEmpty()) { previewLines.add(""); continue; }
            int start = 0;
            while (start < line.length()) {
                int end = start;
                Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 15);
                while (end < line.length()) {
                    int next = line.offsetByCodePoints(end, 1);
                    if (end > start && font.getStringBounds(line.substring(start, next), METRICS).getWidth() > width) break;
                    end = next;
                }
                previewLines.add(line.substring(start, end)); start = end;
            }
        }
    }
    private static String fit(String value, int width, int size) {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, size);
        if (font.getStringBounds(value, METRICS).getWidth() <= width) return value;
        int end = value.length();
        while (end > 0 && font.getStringBounds(value.substring(0, end) + "...", METRICS).getWidth() > width) end = value.offsetByCodePoints(end, -1);
        return value.substring(0, end) + "...";
    }
    private static String size(long bytes) { return bytes < 1024 ? bytes + " B" : bytes < 1048576 ? String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0) : String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0); }
    private static final class ToolButton extends Button {
        ToolButton(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            if (isPressed()) canvas.fillRect(1, 1, getWidth() - 2, getHeight() - 2, 0xffc9d8ec);
            canvas.strokeRect(0, 0, getWidth(), getHeight(), 0xff8c99a8);
            Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
            int width = (int) font.getStringBounds(getText(), METRICS).getWidth();
            canvas.drawText(getText(), Math.max(4, (getWidth() - width) / 2), (getHeight() - 14) / 2 + 13, 14, 0xff202124);
        }
    }
    private final class Row extends View {
        final int index;
        Row(Context context, int index) { super(context); this.index = index; }
        FileOperations.Entry entry() { int i = page * pageSize + index; return i < filtered.size() ? filtered.get(i) : null; }
        @Override protected void onDraw(Canvas canvas) {
            FileOperations.Entry entry = entry(); if (entry == null) return;
            canvas.fillRect(0, 0, getWidth(), getHeight(), entry.path.equals(selected) || isPressed() ? 0xffdce7ff : 0xffffffff);
            canvas.fillRect(10, 12, 28, 25, entry.directory ? 0xffe7b24a : 0xff84a7e8);
            if (entry.directory) canvas.fillRect(10, 8, 14, 8, 0xffe7b24a);
            canvas.drawText(fit(entry.name, getWidth() - 65, 16), 50, 21, 16, 0xff182230);
            canvas.drawText((entry.directory ? "Folder" : size(entry.size)) + (entry.link ? "  |  Link" : ""), 50, 39, 12, 0xff667085);
        }
    }
    private final class Input extends View {
        String text = ""; final String hint; final int limit; int cursor; boolean all;
        Input(Context context, String hint, int limit) { super(context); this.hint = hint; this.limit = limit; }
        void set(String value) { text = value; cursor = value.length(); all = false; }
        boolean key(KeyEvent event) {
            int key = event.getValue();
            if (event.getType() == KeyEvent.TEXT) {
                if (!Character.isValidCodePoint(key) || Character.isISOControl(key)) return false;
                String inserted = new String(Character.toChars(key));
                if ((all ? 0 : text.length()) + inserted.length() > limit) return true;
                if (all) set(""); text = text.substring(0, cursor) + inserted + text.substring(cursor); cursor += inserted.length(); return true;
            }
            if (event.getType() != KeyEvent.DOWN) return false;
            if ((event.getModifiers() & KeyEvent.CONTROL) != 0 && (key == 'a' || key == 'A')) { all = true; return true; }
            if (key == KeyEvent.HOME) cursor = 0;
            else if (key == KeyEvent.END) cursor = text.length();
            else if (key == KeyEvent.LEFT) { if (cursor > 0) cursor = text.offsetByCodePoints(cursor, -1); }
            else if (key == KeyEvent.RIGHT) { if (cursor < text.length()) cursor = text.offsetByCodePoints(cursor, 1); }
            else if (key == KeyEvent.BACKSPACE || key == KeyEvent.DELETE) {
                if (all) set("");
                else if (key == KeyEvent.BACKSPACE && cursor > 0) {
                    int before = text.offsetByCodePoints(cursor, -1); text = text.substring(0, before) + text.substring(cursor); cursor = before;
                } else if (key == KeyEvent.DELETE && cursor < text.length()) text = text.substring(0, cursor) + text.substring(text.offsetByCodePoints(cursor, 1));
            } else return false;
            all = false; return true;
        }
        @Override protected void onDraw(Canvas canvas) {
            canvas.fillRect(0, 0, getWidth(), getHeight(), focused == this && all ? 0xffdce7ff : 0xffffffff);
            canvas.strokeRect(0, 0, getWidth(), getHeight(), focused == this ? 0xff5179db : 0xffccd4e0);
            Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 15); int start = 0;
            while (start < cursor && font.getStringBounds(text.substring(start, cursor), METRICS).getWidth() > getWidth() - 28) start = text.offsetByCodePoints(start, 1);
            canvas.save();
            try {
                canvas.clipRect(8, 4, Math.max(0, getWidth() - 16), Math.max(0, getHeight() - 8));
                canvas.drawText(text.isEmpty() ? hint : text.substring(start), 12, 26, 15, text.isEmpty() ? 0xff98a2b3 : 0xff182230);
                if (focused == this) canvas.fillRect(12 + (int) font.getStringBounds(text.substring(start, cursor), METRICS).getWidth(), 8, 1, 24, 0xff5179db);
            } finally { canvas.restore(); }
        }
    }
}
