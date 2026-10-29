/*
 * Copyright (C) 2006 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.widget;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.util.AttributeSet;
import com.android.view.KeyEvent;
import com.android.view.MotionEvent;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

/**
 * Editable TextView for this framework's committed Unicode/X11 input pipeline.
 * Follows Android selection and movement semantics, using UTF-16 offsets.
 * Unlike Android's Editable/Editor stack, getText() retains TextView's String API.
 * Supports plain text, soft wrapping, selection, desktop clipboard and callbacks.
 * InputType, spans, composing IME text and undo are not implemented here.
 */
public class EditText extends TextView {
    private int anchor, cursor, scrollX, scrollY;
    private int maxCodePoints = Integer.MAX_VALUE;
    private int maxLength = Integer.MAX_VALUE, minLines = 1, maxLines = Integer.MAX_VALUE;
    private String hint = "";
    private int hintColor = 0xff80868b;
    private boolean singleLine, selectAllOnFocus, cursorVisible = true, dragging;
    private OnTextChangedListener textListener;
    private OnEditorActionListener actionListener;

    /** Framework callback; text is an immutable snapshot, not Android Editable. */
    public interface OnTextChangedListener { void onTextChanged(EditText view, String text); }
    public interface OnEditorActionListener { boolean onEditorAction(EditText view, KeyEvent event); }

    public EditText(Context context) { this(context, null); }
    public EditText(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public EditText(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }
    public EditText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setFocusable(true);
        if (attrs == null || attrs.get("padding") == null) mPadding = 8;
        if (mBackground == null) mBackground = 0xffffffff;
        if (attrs != null) {
            hint = attrs.get("hint") == null ? "" : attrs.get("hint");
            hintColor = attrs.color("textColorHint", hintColor);
            singleLine = "true".equals(attrs.get("singleLine"));
            selectAllOnFocus = "true".equals(attrs.get("selectAllOnFocus"));
            cursorVisible = !"false".equals(attrs.get("cursorVisible"));
            maxLength = nonnegative(attrs, "maxLength", maxLength);
            minLines = Math.max(1, nonnegative(attrs, "minLines", minLines));
            maxLines = Math.max(minLines, nonnegative(attrs, "maxLines", maxLines));
            if (attrs.get("lines") != null) minLines = maxLines = Math.max(1, nonnegative(attrs, "lines", 1));
        }
        mText = filtered(mText, maxLength);
    }
    private static int nonnegative(AttributeSet attrs, String name, int fallback) {
        String value = attrs.get(name);
        if (value == null) return fallback;
        int result = Integer.parseInt(value);
        if (result < 0) throw new IllegalArgumentException(name + " must be nonnegative");
        return result;
    }
    public void setOnTextChangedListener(OnTextChangedListener listener) { textListener = listener; }
    public void setOnEditorActionListener(OnEditorActionListener listener) { actionListener = listener; }
    public String getHint() { return hint; }
    public void setHint(String value) { hint = value == null ? "" : value; requestLayout(); invalidate(); }
    public void setHintTextColor(int color) { hintColor = color; invalidate(); }
    public boolean isSingleLine() { return singleLine; }
    public void setSingleLine() { setSingleLine(true); }
    public void setSingleLine(boolean value) {
        singleLine = value;
        setText(mText);
    }
    public void setSelectAllOnFocus(boolean value) { selectAllOnFocus = value; }
    public void setCursorVisible(boolean value) { cursorVisible = value; invalidate(); }
    /** Optional application limit in Unicode code points, independent of maxLength. */
    public void setMaxCodePoints(int value) {
        if (value < 0) throw new IllegalArgumentException("maxCodePoints must be nonnegative");
        maxCodePoints = value;
        setText(mText);
    }
    public int getMaxCodePoints() { return maxCodePoints; }
    public int getMaxLength() { return maxLength; }
    public void setMaxLength(int value) {
        if (value < 0) throw new IllegalArgumentException("maxLength must be nonnegative");
        maxLength = value;
        int oldAnchor = anchor, oldCursor = cursor;
        setText(mText);
        setSelection(Math.min(oldAnchor, mText.length()), Math.min(oldCursor, mText.length()));
    }
    public int getSelectionStart() { return anchor; }
    public int getSelectionEnd() { return cursor; }
    public boolean hasSelection() { return anchor != cursor; }
    public void setSelection(int index) { setSelection(index, index); }
    public void setSelection(int start, int end) {
        if (start < 0 || end < 0 || start > mText.length() || end > mText.length())
            throw new IndexOutOfBoundsException("Selection outside text");
        anchor = boundary(mText, start); cursor = boundary(mText, end);
        ensureCursorVisible();
        invalidate();
    }
    public void selectAll() { setSelection(0, mText.length()); }
    public void extendSelection(int index) { setSelection(anchor, index); }
    private static int boundary(String text, int offset) {
        return offset > 0 && offset < text.length() && Character.isHighSurrogate(text.charAt(offset - 1))
                && Character.isLowSurrogate(text.charAt(offset)) ? offset - 1 : offset;
    }
    private String filtered(String value, int limit) {
        String text = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
        if (singleLine) text = text.replace('\n', ' ');
        int codePointEnd = text.offsetByCodePoints(0, Math.min(maxCodePoints, text.codePointCount(0, text.length())));
        return text.substring(0, boundary(text, Math.min(limit, codePointEnd)));
    }
    @Override public void setText(String value) {
        String next = filtered(value, maxLength);
        boolean changed = !mText.equals(next);
        mText = next;
        anchor = cursor = scrollX = scrollY = 0;
        requestLayout(); invalidate();
        if (changed && textListener != null) textListener.onTextChanged(this, mText);
    }
    public void append(String text) {
        setSelection(mText.length());
        replaceSelection(text);
    }
    public void replaceSelection(String text) {
        int start = Math.min(anchor, cursor), end = Math.max(anchor, cursor);
        String insert = filtered(text, Math.max(0, maxLength - (mText.length() - (end - start))));
        int remaining = maxCodePoints - mText.codePointCount(0, start) - mText.codePointCount(end, mText.length());
        insert = insert.substring(0, insert.offsetByCodePoints(0, Math.min(Math.max(0, remaining), insert.codePointCount(0, insert.length()))));
        String next = mText.substring(0, start) + insert + mText.substring(end);
        boolean changed = !mText.equals(next);
        mText = next;
        anchor = cursor = start + insert.length();
        ensureCursorVisible();
        requestLayout(); invalidate();
        if (changed && textListener != null) textListener.onTextChanged(this, mText);
    }
    @Override protected void onFocusChanged(boolean focused) {
        dragging = false;
        if (focused && selectAllOnFocus) selectAll();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (!isFocused()) return false;
        int key = event.getValue(), mods = event.getModifiers();
        boolean shift = (mods & KeyEvent.SHIFT) != 0, control = (mods & KeyEvent.CONTROL) != 0;
        if (event.getType() == KeyEvent.TEXT) {
            if (control || !Character.isValidCodePoint(key) || Character.isISOControl(key)
                    || (key >= 0xd800 && key <= 0xdfff)) return false;
            replaceSelection(new String(Character.toChars(key)));
            return true;
        }
        if (event.getType() != KeyEvent.DOWN) return false;
        if (control) {
            switch (Character.toLowerCase(key)) {
                case 'a': selectAll(); return true;
                case 'c': return copy(false);
                case 'x': return copy(true);
                case 'v': return paste();
                default: break;
            }
        }
        int target = cursor;
        switch (key) {
            case KeyEvent.LEFT:
                target = !shift && hasSelection() ? Math.min(anchor, cursor)
                        : cursor == 0 ? 0 : mText.offsetByCodePoints(cursor, -1); break;
            case KeyEvent.RIGHT:
                target = !shift && hasSelection() ? Math.max(anchor, cursor)
                        : cursor == mText.length() ? cursor : mText.offsetByCodePoints(cursor, 1); break;
            case KeyEvent.HOME: target = control ? 0 : lines(mText, contentWidth()).get(lineAt(cursor)).start; break;
            case KeyEvent.END:
                List<Line> lines = lines(mText, contentWidth());
                target = control ? mText.length() : lines.get(lineAt(cursor)).end; break;
            case KeyEvent.ARROW_UP: case KeyEvent.ARROW_DOWN:
                if (singleLine) return false;
                List<Line> rows = lines(mText, contentWidth());
                int row = lineAt(cursor), next = Math.max(0, Math.min(rows.size() - 1,
                        row + (key == KeyEvent.ARROW_UP ? -1 : 1)));
                target = offsetAt(rows.get(next), measureText(mText.substring(rows.get(row).start, cursor))); break;
            case KeyEvent.BACKSPACE: case KeyEvent.DELETE:
                if (!hasSelection()) {
                    if (key == KeyEvent.BACKSPACE && cursor > 0) anchor = mText.offsetByCodePoints(cursor, -1);
                    else if (key == KeyEvent.DELETE && cursor < mText.length()) anchor = mText.offsetByCodePoints(cursor, 1);
                }
                replaceSelection(""); return true;
            case KeyEvent.ENTER:
                if (actionListener != null && actionListener.onEditorAction(this, event)) return true;
                if (singleLine) return false;
                replaceSelection("\n"); return true;
            default: return false;
        }
        setSelection(shift ? anchor : target, target);
        return true;
    }
    private boolean copy(boolean cut) {
        if (!hasSelection()) return true;
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(
                    mText.substring(Math.min(anchor, cursor), Math.max(anchor, cursor))), null);
            if (cut) replaceSelection("");
            return true;
        } catch (RuntimeException unavailable) { return false; }
    }
    private boolean paste() {
        try {
            Object value = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (value instanceof String) replaceSelection((String) value);
            return true;
        } catch (Exception unavailable) { return false; }
    }
    @Override protected boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_CANCEL) { dragging = false; return true; }
        if (action == MotionEvent.ACTION_SCROLL && !singleLine) {
            scrollY = Math.max(0, Math.min(maxScrollY(), scrollY - Integer.signum(event.getScrollDelta()) * 3 * lineHeight()));
            invalidate(); return true;
        }
        if (event.getButton() != 1) return false;
        if (action == MotionEvent.ACTION_DOWN) {
            boolean alreadyFocused = isFocused();
            if (!requestFocus()) return false;
            ensureCursorVisible();
            if (alreadyFocused || !selectAllOnFocus) {
                int offset = offsetAt(event.getX(), event.getY());
                setSelection((event.getModifiers() & KeyEvent.SHIFT) != 0 ? anchor : offset, offset);
            }
            dragging = true; return true;
        }
        if ((action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) && dragging) {
            if (action == MotionEvent.ACTION_MOVE) extendSelection(offsetAt(event.getX(), event.getY()));
            else { dragging = false; performClick(); }
            return true;
        }
        return false;
    }
    private static final class Line {
        final int start, end;
        Line(int start, int end) { this.start = start; this.end = end; }
    }
    private int contentWidth() { return Math.max(1, getWidth() - 2 * mPadding - 2); }
    private int lineHeight() { return mTextSize + 8; }
    protected int measureText(String text) {
        return (int) Math.ceil(com.android.graphics.FontManager.measureText(text, mTextSize));
    }
    /** Override together with measureText when using a custom typeface. */
    protected void drawTextLine(Canvas canvas, String value, int x, int baseline, int color) {
        canvas.drawText(value, x, baseline, mTextSize, color);
    }
    private List<Line> lines(String text, int available) {
        List<Line> result = new ArrayList<>();
        int start = 0, end = 0;
        while (end < text.length()) {
            if (!singleLine && text.charAt(end) == '\n') {
                result.add(new Line(start, end)); start = ++end; continue;
            }
            int next = text.offsetByCodePoints(end, 1);
            if (!singleLine && end > start && measureText(text.substring(start, next)) > available) {
                result.add(new Line(start, end)); start = end;
            }
            end = next;
        }
        result.add(new Line(start, end));
        return result;
    }
    private int lineAt(int offset) {
        List<Line> rows = lines(mText, contentWidth());
        int row = 0;
        while (row + 1 < rows.size() && rows.get(row + 1).start <= offset) row++;
        return row;
    }
    private int offsetAt(Line row, int x) {
        int previousWidth = 0;
        for (int index = row.start; index < row.end;) {
            int next = mText.offsetByCodePoints(index, 1);
            int nextWidth = measureText(mText.substring(row.start, next));
            if (x < (previousWidth + nextWidth) / 2) return index;
            previousWidth = nextWidth; index = next;
        }
        return row.end;
    }
    private int offsetAt(int x, int y) {
        List<Line> rows = lines(mText, contentWidth());
        int row = Math.max(0, Math.min(rows.size() - 1, Math.floorDiv(y - mPadding + scrollY, lineHeight())));
        return offsetAt(rows.get(row), x - mPadding + scrollX);
    }
    private int maxScrollY() {
        return Math.max(0, lines(mText, contentWidth()).size() * lineHeight() - Math.max(1, getHeight() - 2 * mPadding));
    }
    private void ensureCursorVisible() {
        List<Line> rows = lines(mText, contentWidth());
        int row = lineAt(cursor);
        int x = measureText(mText.substring(rows.get(row).start, cursor));
        if (singleLine) scrollX = Math.max(0, Math.min(Math.min(scrollX, x), Math.max(0, measureText(mText) - contentWidth())));
        else scrollX = 0;
        if (x - scrollX > contentWidth()) scrollX = x - contentWidth();
        int height = Math.max(1, getHeight() - 2 * mPadding);
        if (row * lineHeight() < scrollY) scrollY = row * lineHeight();
        if ((row + 1) * lineHeight() > scrollY + height) scrollY = (row + 1) * lineHeight() - height;
        scrollY = Math.max(0, Math.min(scrollY, maxScrollY()));
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        String text = mText.isEmpty() ? hint : mText;
        int desired = Math.max(measureText("MMMMMMMMMM"), measureText(text.replace('\n', ' '))) + 2 * mPadding + 2;
        int measuredWidth = resolveSize(desired, widthSpec);
        int count = singleLine ? 1 : Math.max(minLines, Math.min(maxLines,
                lines(text, Math.max(1, measuredWidth - 2 * mPadding - 2)).size()));
        setMeasuredDimension(measuredWidth, resolveSize(count * lineHeight() + 2 * mPadding, heightSpec));
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.strokeRect(0, 0, getWidth(), getHeight(), isFocused() ? 0xff4285f4 : 0xff9aa0a6);
        canvas.save();
        try {
            canvas.clipRect(mPadding, mPadding, Math.max(0, getWidth() - 2 * mPadding), Math.max(0, getHeight() - 2 * mPadding));
            String text = mText.isEmpty() ? hint : mText;
            List<Line> rows = lines(text, contentWidth());
            int start = Math.min(anchor, cursor), end = Math.max(anchor, cursor);
            int cursorRow = lineAt(cursor);
            for (int i = 0; i < rows.size(); i++) {
                Line row = rows.get(i);
                int y = mPadding + i * lineHeight() - scrollY, x = mPadding - scrollX;
                if (y + lineHeight() <= mPadding || y >= getHeight() - mPadding) continue;
                if (isFocused() && !mText.isEmpty() && start <= row.end && end > row.start) {
                    int left = measureText(text.substring(row.start, Math.max(row.start, start)));
                    int right = measureText(text.substring(row.start, Math.min(row.end, end)));
                    if (end > row.end) right += 3;
                    canvas.fillRect(x + left, y, right - left, lineHeight(), 0xffc2dbff);
                }
                drawTextLine(canvas, text.substring(row.start, row.end), x, y + mTextSize,
                        mText.isEmpty() ? hintColor : mTextColor);
                if (isFocused() && cursorVisible && i == cursorRow) {
                    int caretX = x + (mText.isEmpty() ? 0 : measureText(mText.substring(row.start, cursor)));
                    canvas.fillRect(caretX, y, 1, lineHeight(), 0xff1a73e8);
                }
            }
        } finally { canvas.restore(); }
    }
    // Scroll only when editing/moving/resizing; wheel scrolling must survive a redraw.
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed) ensureCursorVisible();
    }
}