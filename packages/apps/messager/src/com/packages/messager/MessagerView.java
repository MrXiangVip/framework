package com.packages.messager;

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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/** Local conversation manager rendered by the framework View tree, with real X11 text input. */
public class MessagerView extends ViewGroup {
    private boolean updatingInputs;
    private final MessageStore store;
    private List<MessageStore.Message> messages = new ArrayList<>(), history = new ArrayList<>();
    private final List<Conversation> conversations = new ArrayList<>();
    private final Map<String, Control> controls = new LinkedHashMap<>();
    private final ConversationRow[] rows = new ConversationRow[8];
    private final EditText search, recipient, body;
    private final History surface;
    private boolean busy, editing, dirty, creating;
    private String current = "", status = "", dialog = "";
    private int page, historyPage, capacity = 5, inset = 24;
    private final Handler ui = new Handler(message -> { ((Runnable) message.obj).run(); return true; });
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> { Thread worker = new Thread(r, "messager-store"); worker.setDaemon(true); return worker; });
    private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);
    private static final int INK = 0xff20324e, MUTED = 0xff697b95, ACCENT = 0xff306bcc;
    private static final class Conversation {
        final String phone; MessageStore.Message last, draft; int count;
        Conversation(String phone) { this.phone = phone; }
    }
    public MessagerView(Context context, AttributeSet attrs) {
        super(context, attrs); mBackground = 0xfff4f7fc;
        store = new MessageStore(Paths.get(System.getProperty("framework.messager.file", Paths.get(System.getProperty("user.home"), ".framework", "messages.dat").toString())));
        surface = new History(context); surface.setId("history"); addView(surface, new LayoutParams(-2, -2));
        control("new", "+ New", () -> open("")); control("refresh", "Refresh", this::load);
        control("back", "< Back", this::back);
        control("save_draft", "Save draft", () -> save(true)); control("send", "Save locally", () -> save(false));
        control("delete", "Delete chat", () -> { dialog = "delete"; focus(null); requestLayout(); invalidate(); });
        control("confirm", "Confirm", this::confirm);
        control("cancel", "Cancel", () -> { dialog = ""; requestLayout(); invalidate(); });
        control("previous", "< Previous", () -> { if (editing) { if (historyPage > 0) { historyPage--; surface.reset(); } } else if (page > 0) page--; invalidate(); });
        control("next", "Next >", () -> { if (editing) { if ((historyPage + 1) * 10 < history.size()) { historyPage++; surface.reset(); } } else if ((page + 1) * capacity < conversations.size()) page++; invalidate(); });
        control("up", "Up", () -> surface.scroll(-150)); control("down", "Down", () -> surface.scroll(150));
        search = input("search", "Search phone or message", false, 100);
        recipient = input("recipient", "Phone number", false, 40); body = input("body", "Write a message...", true, 2000);

        for (int i = 0; i < rows.length; i++) { rows[i] = new ConversationRow(context, i); rows[i].setId("row_" + i); addView(rows[i], new LayoutParams(-2, -2)); }
        load();
    }
    private EditText input(String id, String hint, boolean multiline, int limit) {
        EditText field = new EditText(getContext());
        field.setId(id); field.setHint(hint); field.setSingleLine(!multiline); field.setMaxCodePoints(limit); field.setTextSize(14);
        field.setOnTextChangedListener((view, text) -> {
            if (updatingInputs) return;
            if ("search".equals(view.getId())) { page = 0; rebuild(); }
            else { dirty = true; status = "Unsaved changes"; }
            invalidate();
        });
        addView(field, new LayoutParams(-2, -2)); return field;
    }
    private void control(String id, String text, Runnable action) {
        Control button = new Control(getContext(), text); button.setId(id);
        button.setOnClickListener(v -> { if (!busy) action.run(); }); controls.put(id, button); addView(button, new LayoutParams(-2, -2));
    }
    private interface Work { Runnable run() throws Exception; }
    private void work(String description, Work task) {
        if (busy) return;
        busy = true; status = description; invalidate();
        io.execute(() -> {
            Runnable action;
            try { action = task.run(); }
            catch (Exception e) { String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); action = () -> status = "Error: " + error; }
            final Runnable done = action;
            ui.sendMessage(ui.obtainMessage(0, (Runnable) () -> { busy = false; done.run(); requestLayout(); invalidate(); }));
        });
    }
    private void load() { work("Loading messages...", () -> { List<MessageStore.Message> loaded = store.load(); return () -> { messages = loaded; rebuild(); status = "Local messages loaded"; }; }); }
    private void rebuild() {
        Map<String, Conversation> groups = new HashMap<>();
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        for (MessageStore.Message message : messages) {
            Conversation conversation = groups.computeIfAbsent(message.recipient, Conversation::new);
            if (conversation.last == null || message.time >= conversation.last.time) conversation.last = message;
            if (message.kind.equals(MessageStore.DRAFT)) conversation.draft = message; else conversation.count++;
        }
        conversations.clear();
        for (Conversation conversation : groups.values()) {
            boolean matches = conversation.phone.toLowerCase(Locale.ROOT).contains(query);
            if (!matches) for (MessageStore.Message message : messages) if (message.recipient.equals(conversation.phone) && message.body.toLowerCase(Locale.ROOT).contains(query)) { matches = true; break; }
            if (matches) conversations.add(conversation);
        }
        conversations.sort(Comparator.comparingLong((Conversation c) -> c.last.time).reversed().thenComparing(c -> c.phone));
        page = Math.min(page, Math.max(0, (conversations.size() - 1) / capacity));
        history.clear();
        for (MessageStore.Message message : messages) if (message.recipient.equals(current) && message.kind.equals(MessageStore.OUTBOX)) history.add(message);
        historyPage = Math.min(historyPage, Math.max(0, (history.size() - 1) / 10)); surface.reset();
    }
    private void open(String phone) {
        current = phone; creating = phone.isEmpty(); editing = true; dirty = false; dialog = "";
        setInput(recipient, phone); setInput(body, "");
        for (MessageStore.Message message : messages) if (message.recipient.equals(phone) && message.kind.equals(MessageStore.DRAFT)) setInput(body, message.body);
        rebuild(); historyPage = Math.max(0, (history.size() - 1) / 10); surface.reset();
        focus(creating ? recipient : body); status = "Local only - no SMS delivery"; requestLayout(); invalidate();
    }
    private void back() {
        if (dirty) { dialog = "discard"; focus(null); requestLayout(); invalidate(); }
        else list();
    }
    private void list() { editing = false; dirty = false; dialog = ""; focus(null); current = ""; status = "Local messages only"; rebuild(); requestLayout(); invalidate(); }
    private void confirm() {
        if (dialog.equals("discard")) { list(); return; }
        if (dialog.equals("delete")) {
            final String phone = current;
            work("Deleting conversation...", () -> { List<MessageStore.Message> loaded = store.deleteConversation(phone); return () -> { messages = loaded; list(); status = "Conversation deleted"; }; });
        }
    }
    private void save(boolean draft) {
        final String phone, text = body.getText();
        try {
            phone = MessageStore.normalizeRecipient(creating ? recipient.getText() : current);
            MessageStore.validate(new MessageStore.Message(UUID.randomUUID().toString(), phone, text.trim(), draft ? MessageStore.DRAFT : MessageStore.OUTBOX, System.currentTimeMillis()));
        } catch (IllegalArgumentException e) { status = e.getMessage(); invalidate(); return; }
        work(draft ? "Saving draft..." : "Saving local message...", () -> {
            List<MessageStore.Message> loaded = store.save(phone, text, draft);
            return () -> {
                messages = loaded; current = phone; setInput(recipient, phone); creating = false; dirty = false;
                if (draft) setInput(body, text.trim()); else setInput(body, "");
                focus(body); rebuild(); historyPage = Math.max(0, (history.size() - 1) / 10); surface.reset();
                status = draft ? "Draft saved" : "Saved locally - not sent as SMS";
            };
        });
    }
    public boolean isBusy() { return busy; }
    public boolean isEditing() { return editing; }
    public boolean isDirty() { return dirty; }
    public String getStatus() { return status; }
    public String getDialog() { return dialog; }
    public String getBody() { return body.getText(); }
    public String getRecipient() { return recipient.getText(); }
    public int getConversationCount() { return conversations.size(); }
    public int getMessageCount() { return messages.size(); }
    public int getHistoryCount() { return history.size(); }
    public int getHistoryPage() { return historyPage; }
    public String getRowRecipient(int row) { int index = page * capacity + row; return index < conversations.size() ? conversations.get(index).phone : ""; }
    public int getHistoryScroll() { return surface.offset; }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (busy && event.getAction() != MotionEvent.ACTION_CANCEL) return true;
        return super.dispatchTouchEvent(event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (busy) return true;
        if (event.getType() == KeyEvent.DOWN && (event.getValue() == KeyEvent.TAB || event.getValue() == KeyEvent.ISO_LEFT_TAB))
            return super.dispatchKeyEvent(event);

        int key = event.getValue();
        if (event.getType() == KeyEvent.DOWN) {
            if (key == KeyEvent.ESCAPE) { if (!dialog.isEmpty()) { dialog = ""; requestLayout(); invalidate(); } else if (editing) back(); else { focus(null); invalidate(); } return true; }
            if (!dialog.isEmpty()) return true;

            if (editing && key == KeyEvent.ENTER && (event.getModifiers() & KeyEvent.CONTROL) != 0) { save(false); return true; }
            if (editing && key == KeyEvent.ENTER && focused() == recipient) { focus(body); invalidate(); return true; }
            if (editing && (key == 0xff55 || key == 0xff56)) { surface.scroll(key == 0xff55 ? -150 : 150); return true; }
        }
        if (dialog.isEmpty() && focused() != null && focused().dispatchKeyEvent(event)) {
            return true;
        }
        return false;
    }
    private EditText focused() {
        View view = findFocus();
        return view instanceof EditText ? (EditText) view : null;
    }
    private void focus(EditText field) {
        clearFocus();
        if (field != null) { field.requestFocus(); field.setSelection(field.getText().length()); }
    }
    private void setInput(EditText field, String value) {
        updatingInputs = true;
        try {
            field.setText(value);
            field.setSelection(field.getText().length());
        } finally { updatingInputs = false; }
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(600, widthSpec), resolveSize(800, heightSpec)); inset = Math.min(24, getMeasuredWidth() / 20);
        int previous = capacity; capacity = Math.max(1, Math.min(8, (getMeasuredHeight() - 260) / 86));
        if (previous != capacity) page = Math.min(page, Math.max(0, (conversations.size() - 1) / capacity));
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(40, MeasureSpec.EXACTLY));
    }
    private void place(View view, int x, int y, int width, int height) { view.layout(x, y, x + Math.max(0, width), y + Math.max(0, height)); }
    private void place(String id, int x, int y, int width, int height) { place(controls.get(id), x, y, width, height); }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) place(getChildAt(i), 0, 0, 0, 0);
        int width = getWidth() - 2 * inset, height = getHeight();
        if (!dialog.isEmpty()) { place("confirm", inset, 278, width, 42); place("cancel", inset, 332, width, 42); return; }
        if (!editing) {
            place(search, inset, 90, width, 40);
            place("new", inset, 142, 110, 36); place("refresh", getWidth() - inset - 100, 142, 100, 36);
            for (int i = 0; i < capacity; i++) place(rows[i], inset, 194 + i * 86, width, 78);
            place("previous", inset, height - 72, 100, 38); place("next", getWidth() - inset - 100, height - 72, 100, 38);
        } else {
            place("back", inset, 90, 78, 36);
            if (!creating) place("delete", getWidth() - inset - 100, 90, 100, 36);
            if (creating) place(recipient, inset, 142, width, 40);
            int top = creating ? 194 : 142, composeTop = height - 224;
            place(surface, inset, top, width, Math.max(24, composeTop - top - 48));
            int navY = composeTop - 38, quarter = (width - 18) / 4;
            String[] nav = {"previous", "up", "down", "next"};
            for (int i = 0; i < 4; i++) place(nav[i], inset + i * (quarter + 6), navY, quarter, 30);
            place(body, inset, composeTop + 16, width, 126);
            int half = (width - 10) / 2;
            place("save_draft", inset, height - 70, half, 38); place("send", inset + half + 10, height - 70, half, 38);
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawText("MESSAGES", inset, 39, 25, INK);
        canvas.drawText("Local messages - no SMS delivery", inset, 65, 13, MUTED);
        int width = getWidth() - 2 * inset;
        if (!dialog.isEmpty()) {
            canvas.drawText(dialog.equals("delete") ? "Delete this conversation?" : "Discard unsaved changes?", inset, 176, Math.min(22, getWidth() / 15), INK);
            canvas.drawText(dialog.equals("delete") ? "All local messages and its draft will be removed." : "Use Save draft to keep your message first.", inset, 214, Math.min(14, getWidth() / 29), MUTED);
        } else if (editing) {
            if (!creating) canvas.drawText(fit(current, width - 192, 16), inset + 88, 114, 16, INK);
            canvas.drawText("Message  " + body.getText().codePointCount(0, body.getText().length()) + "/2000", inset, getHeight() - 215, 12, MUTED);
        } else {
            if (conversations.isEmpty()) {
                canvas.drawText(search.getText().isEmpty() ? "No conversations yet" : "No matching conversations", inset, 239, Math.min(21, getWidth() / 16), INK);
                canvas.drawText("Choose + New to write a local message.", inset, 273, Math.min(14, getWidth() / 28), MUTED);
            }
            canvas.drawText((page + 1) + " / " + Math.max(1, (conversations.size() + capacity - 1) / capacity), getWidth() / 2 - 18, getHeight() - 47, 14, MUTED);
        }
        canvas.drawText(fit(status, width, 12), inset, getHeight() - 13, 12, ACCENT);
    }
    private static String fit(String value, int width, int size) {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, size); int end = value.length();
        while (end > 0 && font.getStringBounds(value.substring(0, end) + (end < value.length() ? "..." : ""), METRICS).getWidth() > width) end = value.offsetByCodePoints(end, -1);
        return value.substring(0, end) + (end < value.length() ? "..." : "");
    }
    private static List<String> wrap(String text, int width, int size) {
        List<String> lines = new ArrayList<>(); Font font = new Font(Font.SANS_SERIF, Font.PLAIN, size);
        StringBuilder line = new StringBuilder(); double length = 0;
        for (int offset = 0; offset < text.length();) {
            int code = text.codePointAt(offset); offset += Character.charCount(code);
            if (code == '\n') { lines.add(line.toString()); line.setLength(0); length = 0; continue; }
            String glyph = new String(Character.toChars(code)); double advance = font.getStringBounds(glyph, METRICS).getWidth();
            if (line.length() > 0 && length + advance > Math.max(20, width)) { lines.add(line.toString()); line.setLength(0); length = 0; }
            line.append(glyph); length += advance;
        }
        lines.add(line.toString()); return lines;
    }
    private static String date(long time) { return new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(new Date(time)); }
    private final class ConversationRow extends View {
        final int row;
        ConversationRow(Context context, int row) { super(context); this.row = row; setOnClickListener(v -> { int index = page * capacity + row; if (!busy && index < conversations.size()) open(conversations.get(index).phone); }); }
        @Override protected void onDraw(Canvas canvas) {
            int index = page * capacity + row; if (index >= conversations.size()) return;
            Conversation conversation = conversations.get(index);
            canvas.fillRect(0, 0, getWidth(), getHeight(), isPressed() ? 0xffdfeaff : 0xffffffff);
            canvas.drawText(fit(conversation.phone, getWidth() - 110, 17), 12, 25, 17, INK);
            canvas.drawText(date(conversation.last.time), getWidth() - 98, 24, 11, MUTED);
            canvas.drawText(fit((conversation.draft == null ? "" : "Draft: ") + conversation.last.body.replace('\n', ' '), getWidth() - 24, 13), 12, 49, 13, MUTED);
            canvas.drawText(conversation.count + " local messages" + (conversation.draft == null ? "" : " / draft saved"), 12, 68, 11, ACCENT);
        }
    }
    private final class History extends View {
        int offset, total, lastY; boolean dragging, bottom = true;
        History(Context context) { super(context); }
        void reset() { bottom = true; invalidate(); }
        void scroll(int distance) { bottom = false; offset = Math.max(0, Math.min(Math.max(0, total - getHeight()), offset + distance)); invalidate(); }
        @Override protected boolean onTouchEvent(MotionEvent event) {
            if (busy) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) { dragging = true; lastY = event.getY(); return true; }
            if (event.getAction() == MotionEvent.ACTION_MOVE && dragging) { scroll(lastY - event.getY()); lastY = event.getY(); return true; }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) { dragging = false; return true; }
            return dragging;
        }
        @Override protected void onDraw(Canvas canvas) {
            canvas.fillRect(0, 0, getWidth(), getHeight(), 0xffeaf0f9);
            List<List<String>> text = new ArrayList<>(); total = 30;
            int start = historyPage * 10, end = Math.min(history.size(), start + 10);
            for (int i = start; i < end; i++) { List<String> lines = wrap(history.get(i).body, getWidth() - 54, 14); text.add(lines); total += lines.size() * 20 + 54; }
            if (bottom) { offset = Math.max(0, total - getHeight()); bottom = false; }
            offset = Math.max(0, Math.min(offset, Math.max(0, total - getHeight())));
            if (history.isEmpty()) { canvas.drawText("No saved local messages", 12, 32, 14, MUTED); return; }
            int y = 26 - offset;
            canvas.drawText("Messages " + (start + 1) + "-" + end + " / " + history.size(), 12, y - 9, 11, MUTED);
            for (int i = start; i < end; i++) {
                List<String> lines = text.get(i - start); int height = lines.size() * 20 + 44;
                if (y + height > 0 && y < getHeight()) {
                    canvas.fillRect(12, y, getWidth() - 30, height, 0xffd5e5ff);
                    canvas.drawText("LOCAL / NOT DELIVERED   " + date(history.get(i).time), 22, y + 18, Math.min(11, getWidth() / 29), ACCENT);
                    for (int line = 0; line < lines.size(); line++) { int baseline = y + 41 + line * 20; if (baseline >= 0 && baseline <= getHeight() + 14) canvas.drawText(lines.get(line), 22, baseline, 14, INK); }
                }
                y += height + 10;
            }
            if (total > getHeight()) { int thumb = Math.max(18, getHeight() * getHeight() / total); int pos = (getHeight() - thumb) * offset / Math.max(1, total - getHeight()); canvas.fillRect(getWidth() - 6, pos, 3, thumb, 0xff94afd8); }
        }
    }
    private final class Control extends Button {
        final String text;
        Control(Context context, String text) { super(context); this.text = text; }
        @Override protected void onDraw(Canvas canvas) {
            boolean primary = "send".equals(getId()) || "new".equals(getId());
            canvas.fillRect(0, 0, getWidth(), getHeight(), primary ? isPressed() ? 0xff214f9b : ACCENT : isPressed() ? 0xffdce8fb : 0xffffffff);
            canvas.strokeRect(0, 0, getWidth(), getHeight(), 0xffc7d5e9);
            String label = fit(text, getWidth() - 8, 13); int width = (int) new Font(Font.SANS_SERIF, Font.PLAIN, 13).getStringBounds(label, METRICS).getWidth();
            canvas.drawText(label, Math.max(4, (getWidth() - width) / 2), getHeight() / 2 + 5, 13, primary ? 0xffffffff : INK);
        }
    }

}
