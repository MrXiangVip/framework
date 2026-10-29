package com.packages.contact;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.util.AttributeSet;
import com.android.view.*;
import com.android.widget.EditText;
import com.android.widget.Button;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

/** Contact list and editor, with one focused text field and real keyboard input. */
public class ContactsView extends ViewGroup {
    private final ContactStore store;
    private List<ContactStore.Contact> contacts = new ArrayList<>(), filtered = new ArrayList<>();
    private final EditText search, name, phone, email;
    private final Button add, previous, next, save, cancel;
    private Button delete;
    private final Row[] rows = new Row[8];
    private boolean editing, confirmingDelete;
    private String editingId, status = "";
    private int page, pageSize = 6, inset;


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
        cancel = button("cancel", "Cancel", () -> { editing = false; focus(search); confirmingDelete = false; reload(); requestLayout(); invalidate(); });
        delete = button("delete", "Delete", () -> {
            if (editingId == null) return;
            if (!confirmingDelete) { confirmingDelete = true; delete.setText("Confirm?"); status = "Click Confirm? again to delete, or Cancel."; invalidate(); return; }
            try { store.delete(editingId); editing = false; focus(search); status = "Contact deleted"; reload(); requestLayout(); }
            catch (IOException | RuntimeException e) { status = "Delete failed: " + e.getMessage(); }
            invalidate();
        });
        for (int i = 0; i < rows.length; i++) {
            final Row row = new Row(context, i); rows[i] = row;
            row.setId("row_" + i); row.setOnClickListener(view -> { ContactStore.Contact value = row.contact(); if (value != null) edit(value); });
            addView(row, new LayoutParams(-2, -2));
        }
        focus(search); reload();
    }
    private EditText field(String id, String hint, int limit) {
        EditText field = new EditText(getContext());
        field.setId(id); field.setHint(hint); field.setSingleLine(); field.setMaxLength(limit); field.setTextSize(17);
        field.setOnTextChangedListener((view, text) -> {
            confirmingDelete = false;
            if (delete != null) delete.setText("Delete");
            if ("search".equals(view.getId())) { page = 0; filter(); }
            invalidate();
        });
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
        String query = search.getText().trim().toLowerCase(Locale.ROOT), digits = query.replaceAll("[^0-9]", "");
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
        setInput(name, contact == null ? "" : contact.name); setInput(phone, contact == null ? "" : contact.phone); setInput(email, contact == null ? "" : contact.email);
        focus(name); confirmingDelete = false; delete.setText("Delete"); status = "";
        requestLayout(); invalidate();
    }
    private void saveContact() {
        try {
            store.save(new ContactStore.Contact(editingId == null ? UUID.randomUUID().toString() : editingId,
                    name.getText(), phone.getText(), email.getText()), editingId == null);
            editing = false; focus(search); setInput(search, ""); page = 0; status = "Contact saved";
            reload(); requestLayout();
        } catch (IOException | RuntimeException e) { status = "Save failed: " + e.getMessage(); }
        invalidate();
    }
    public int getContactCount() { return contacts.size(); }
    public int getFilteredCount() { return filtered.size(); }
    public String getStatus() { return status; }
    public boolean isEditing() { return editing; }
    public String getFieldText(String id) { return ((EditText) findViewById(id)).getText(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getType() == KeyEvent.DOWN && (event.getValue() == KeyEvent.TAB || event.getValue() == KeyEvent.ISO_LEFT_TAB))
            return super.dispatchKeyEvent(event);

        if (event.getType() == KeyEvent.DOWN) {
            int key = event.getValue();

            if (key == KeyEvent.ESCAPE && editing) { cancel.performClick(); return true; }
            if ((key == KeyEvent.ENTER || key == 0xff8d) && editing) { saveContact(); return true; }
        }
        return focused() != null && focused().dispatchKeyEvent(event);
    }
    private EditText focused() {
        View view = findFocus();
        return view instanceof EditText ? (EditText) view : null;
    }
    private void focus(EditText field) {
        clearFocus();
        if (field != null) { field.requestFocus(); field.setSelection(field.getText().length()); }
    }
    private static void setInput(EditText field, String value) {
        field.setText(value);
        field.setSelection(field.getText().length());
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
        if (com.android.graphics.FontManager.measureText(value, size) <= width) return value;
        int end = value.length();
        while (end > 0 && com.android.graphics.FontManager.measureText(value.substring(0, end) + "...", size) > width) end = value.offsetByCodePoints(end, -1);
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

}
