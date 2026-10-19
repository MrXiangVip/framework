package com.packages.contact;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.util.AttributeSet;
import com.android.view.*;
import com.android.widget.Button;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

/** Contact list and editor, with one focused text field and real keyboard input. */
public class ContactsView extends ViewGroup {
    private final ContactStore store;
    private List<ContactStore.Contact> contacts = new ArrayList<>(), filtered = new ArrayList<>();
    private final Field search, name, phone, email;
    private Field focused;
    private final Button add, previous, next, save, cancel;
    private Button delete;
    private final Row[] rows = new Row[8];
    private boolean editing, confirmingDelete;
    private String editingId, status = "";
    private int page, pageSize = 6, inset;
    private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);

    public ContactsView(Context context, AttributeSet attrs) {
        super(context, attrs); mBackground = 0xfff5f7fc;
        store = new ContactStore(new File(System.getProperty("framework.contact.file",
                new File(System.getProperty("user.home"), ".local/share/framework/contacts/contacts.dat").getPath())));
        search = field("search", "Search name, phone or email", 120);
        name = field("name", "Name", 80); phone = field("phone", "Phone", 40); email = field("email", "Email (optional)", 120);
        add = button("add", "New", () -> edit(null));
        previous = button("previous", "<", () -> { if (page > 0) page--; invalidate(); });
        next = button("next", ">", () -> { if ((page + 1) * pageSize < filtered.size()) page++; invalidate(); });
        save = button("save", "Save", this::saveContact);
        cancel = button("cancel", "Cancel", () -> { editing = false; focused = search; confirmingDelete = false; reload(); requestLayout(); invalidate(); });
        delete = button("delete", "Delete", () -> {
            if (editingId == null) return;
            if (!confirmingDelete) { confirmingDelete = true; delete.setText("Confirm?"); status = "Click Confirm? again to delete, or Cancel."; invalidate(); return; }
            try { store.delete(editingId); editing = false; focused = search; status = "Contact deleted"; reload(); requestLayout(); }
            catch (IOException | RuntimeException e) { status = "Delete failed: " + e.getMessage(); }
            invalidate();
        });
        for (int i = 0; i < rows.length; i++) {
            final Row row = new Row(context, i); rows[i] = row;
            row.setId("row_" + i); row.setOnClickListener(view -> { ContactStore.Contact value = row.contact(); if (value != null) edit(value); });
            addView(row, new LayoutParams(-2, -2));
        }
        focused = search; reload();
    }
    private Field field(String id, String hint, int limit) {
        Field field = new Field(getContext(), hint, limit); field.setId(id);
        field.setOnClickListener(view -> { focused = field; field.cursor = field.text.length(); field.selectAll = false; invalidate(); });
        addView(field, new LayoutParams(-2, -2)); return field;
    }
    private Button button(String id, String text, Runnable action) {
        Button button = new Button(getContext()); button.setId(id); button.setText(text);
        button.setOnClickListener(view -> action.run()); addView(button, new LayoutParams(-2, -2)); return button;
    }
    private void reload() {
        try { contacts = store.load(); filter(); }
        catch (IOException | RuntimeException e) { status = "Load failed: " + e.getMessage(); }
    }
    private void filter() {
        String query = search.text.trim().toLowerCase(Locale.ROOT), digits = query.replaceAll("[^0-9]", "");
        filtered = new ArrayList<>();
        for (ContactStore.Contact contact : contacts) {
            String text = (contact.name + " " + contact.phone + " " + contact.email).toLowerCase(Locale.ROOT);
            if (text.contains(query) || (!digits.isEmpty() && query.matches("[0-9+() .-]+")
                    && contact.phone.replaceAll("[^0-9]", "").contains(digits))) filtered.add(contact);
        }
        page = Math.min(page, Math.max(0, (filtered.size() - 1) / pageSize));
    }
    private void edit(ContactStore.Contact contact) {
        editing = true; editingId = contact == null ? null : contact.id;
        name.set(contact == null ? "" : contact.name); phone.set(contact == null ? "" : contact.phone); email.set(contact == null ? "" : contact.email);
        focused = name; confirmingDelete = false; delete.setText("Delete"); status = "";
        requestLayout(); invalidate();
    }
    private void saveContact() {
        try {
            store.save(new ContactStore.Contact(editingId == null ? UUID.randomUUID().toString() : editingId,
                    name.text, phone.text, email.text), editingId == null);
            editing = false; focused = search; search.set(""); page = 0; status = "Contact saved";
            reload(); requestLayout();
        } catch (IOException | RuntimeException e) { status = "Save failed: " + e.getMessage(); }
        invalidate();
    }
    public int getContactCount() { return contacts.size(); }
    public int getFilteredCount() { return filtered.size(); }
    public String getStatus() { return status; }
    public boolean isEditing() { return editing; }
    public String getFieldText(String id) { return ((Field) findViewById(id)).text; }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getType() == KeyEvent.DOWN) {
            int key = event.getValue();
            if (key == KeyEvent.TAB || key == 0xfe20) {
                if (editing) {
                    Field[] fields = {name, phone, email}; int index = Arrays.asList(fields).indexOf(focused);
                    focused = fields[(index + (((event.getModifiers() & KeyEvent.SHIFT) != 0 || key == 0xfe20) ? 2 : 1)) % 3];
                } else focused = search;
                invalidate(); return true;
            }
            if (key == KeyEvent.ESCAPE && editing) { cancel.performClick(); return true; }
            if ((key == KeyEvent.ENTER || key == 0xff8d) && editing) { saveContact(); return true; }
        }
        if (focused == null) return false;
        boolean changed = focused.key(event);
        if (changed) { confirmingDelete = false; delete.setText("Delete"); if (focused == search) { page = 0; filter(); } invalidate(); }
        return changed;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_CANCEL && focused != null) focused.selectAll = false;
        return super.dispatchTouchEvent(event);
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(600, widthSpec), resolveSize(800, heightSpec));
        inset = Math.min(24, getMeasuredWidth() / 20);
        int old = pageSize; pageSize = Math.min(rows.length, Math.max(1, (getMeasuredHeight() - 260) / 70));
        if (old != pageSize) filter();
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(
                MeasureSpec.makeMeasureSpec(Math.max(0, getMeasuredWidth() - inset * 2), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(44, MeasureSpec.EXACTLY));
    }
    private void place(View view, int x, int y, int width, int height) { view.layout(x, y, x + Math.max(0, width), y + Math.max(0, height)); }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) place(getChildAt(i), 0, 0, 0, 0);
        int width = getWidth() - 2 * inset;
        if (editing) {
            place(name, inset, 100, width, 46); place(phone, inset, 184, width, 46); place(email, inset, 268, width, 46);
            int control = (width - 16) / 3, y = getHeight() - 70;
            place(save, inset, y, control, 46); place(cancel, inset + control + 8, y, control, 46);
            if (editingId != null) place(delete, inset + 2 * (control + 8), y, control, 46);
        } else {
            place(add, getWidth() - inset - 90, 25, 90, 42);
            place(search, inset, 92, width, 44);
            for (int i = 0; i < pageSize; i++) place(rows[i], inset, 152 + i * 70, width, 64);
            place(previous, inset, getHeight() - 70, 60, 44);
            place(next, getWidth() - inset - 60, getHeight() - 70, 60, 44);
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawText(editing ? (editingId == null ? "NEW CONTACT" : "EDIT CONTACT") : "CONTACTS", inset, 52, 24, 0xff182230);
        if (editing) {
            canvas.drawText("Name *", inset, 90, 14, 0xff667085); canvas.drawText("Phone", inset, 174, 14, 0xff667085);
            canvas.drawText("Email", inset, 258, 14, 0xff667085);
            canvas.drawText("Enter: save   Tab: next field   Esc: cancel", inset, 342, Math.max(10, Math.min(13, getWidth() / 30)), 0xff667085);
        } else {
            canvas.drawText(filtered.size() + " contacts", inset, 76, 13, 0xff667085);
            if (filtered.isEmpty()) {
                canvas.drawText(contacts.isEmpty() ? "No contacts yet" : "No matching contacts", inset, 192, 20, 0xff667085);
                canvas.drawText("Click New to add a contact", inset, 220, 14, 0xff667085);
            }
            int pages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
            canvas.drawText((page + 1) + " / " + pages, getWidth() / 2 - 20, getHeight() - 42, 16, 0xff667085);
        }
        canvas.drawText(fit(status, Math.max(0, getWidth() - 2 * inset), 12), inset, getHeight() - 92, 12, 0xff39716a);
    }
    private static String fit(String value, int width, int size) {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, size);
        if (font.getStringBounds(value, METRICS).getWidth() <= width) return value;
        int end = value.length();
        while (end > 0 && font.getStringBounds(value.substring(0, end) + "...", METRICS).getWidth() > width) end = value.offsetByCodePoints(end, -1);
        return value.substring(0, end) + "...";
    }
    private final class Row extends View {
        final int index;
        Row(Context context, int index) { super(context); this.index = index; }
        ContactStore.Contact contact() { int i = page * pageSize + index; return i < filtered.size() ? filtered.get(i) : null; }
        @Override protected void onDraw(Canvas canvas) {
            ContactStore.Contact value = contact(); if (value == null) return;
            canvas.fillRect(0, 0, getWidth(), getHeight(), isPressed() ? 0xffdce7ff : 0xffffffff);
            canvas.strokeRect(0, 0, getWidth(), getHeight(), 0xffdfe5ef);
            canvas.fillRect(10, 10, 40, 40, 0xffdce7ff);
            String initial = value.name.substring(0, value.name.offsetByCodePoints(0, 1));
            canvas.drawText(initial, 20, 37, 22, 0xff5179db);
            canvas.drawText(fit(value.name, getWidth() - 80, 18), 64, 26, 18, 0xff182230);
            canvas.drawText(fit(value.phone.isEmpty() ? value.email : value.phone, getWidth() - 80, 13), 64, 49, 13, 0xff667085);
        }
    }
    private final class Field extends View {
        String text = ""; final String hint; final int limit; int cursor; boolean selectAll;
        Field(Context context, String hint, int limit) { super(context); this.hint = hint; this.limit = limit; }
        void set(String value) { text = value; cursor = text.length(); selectAll = false; }
        boolean key(KeyEvent event) {
            int key = event.getValue();
            if (event.getType() == KeyEvent.TEXT) {
                if (!Character.isValidCodePoint(key) || Character.isISOControl(key)) return false;
                String inserted = new String(Character.toChars(key));
                if ((selectAll ? 0 : text.length()) + inserted.length() > limit) return true;
                if (selectAll) set("");
                text = text.substring(0, cursor) + inserted + text.substring(cursor); cursor += inserted.length(); return true;
            }
            if (event.getType() != KeyEvent.DOWN) return false;
            if ((event.getModifiers() & KeyEvent.CONTROL) != 0 && (key == 'a' || key == 'A')) { selectAll = true; return true; }
            if (key == KeyEvent.HOME) cursor = 0;
            else if (key == KeyEvent.END) cursor = text.length();
            else if (key == KeyEvent.LEFT) { if (cursor > 0) cursor = text.offsetByCodePoints(cursor, -1); }
            else if (key == KeyEvent.RIGHT) { if (cursor < text.length()) cursor = text.offsetByCodePoints(cursor, 1); }
            else if (key == KeyEvent.BACKSPACE || key == KeyEvent.DELETE) {
                if (selectAll) set("");
                else if (key == KeyEvent.BACKSPACE && cursor > 0) {
                    int before = text.offsetByCodePoints(cursor, -1); text = text.substring(0, before) + text.substring(cursor); cursor = before;
                } else if (key == KeyEvent.DELETE && cursor < text.length()) text = text.substring(0, cursor) + text.substring(text.offsetByCodePoints(cursor, 1));
            } else return false;
            selectAll = false; return true;
        }
        @Override protected void onDraw(Canvas canvas) {
            canvas.fillRect(0, 0, getWidth(), getHeight(), focused == this && selectAll ? 0xffdce7ff : 0xffffffff);
            canvas.strokeRect(0, 0, getWidth(), getHeight(), focused == this ? 0xff5179db : 0xffccd4e0);
            // Horizontally scroll the text around the insertion point.
            int start = 0;
            Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 17);
            while (start < cursor && font.getStringBounds(text.substring(start, cursor), METRICS).getWidth() > getWidth() - 28)
                start = text.offsetByCodePoints(start, 1);
            canvas.save();
            try {
                canvas.clipRect(8, 4, Math.max(0, getWidth() - 16), Math.max(0, getHeight() - 8));
                canvas.drawText(text.isEmpty() ? hint : text.substring(start), 12, 29, 17, text.isEmpty() ? 0xff98a2b3 : 0xff182230);
                if (focused == this) {
                    int x = 12 + (int) font.getStringBounds(text.substring(start, cursor), METRICS).getWidth();
                    canvas.fillRect(x, 10, 1, 25, 0xff5179db);
                }
            } finally { canvas.restore(); }
        }
    }
}
