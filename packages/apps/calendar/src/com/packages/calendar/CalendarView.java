package com.packages.calendar;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.util.AttributeSet;
import com.android.view.View;
import com.android.view.ViewGroup;
import com.android.widget.Button;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/** Sunday-first month view, rendered and dispatched through the framework View tree. */
public class CalendarView extends ViewGroup {
    private static final String[] WEEKDAYS = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
    private final Button previous, today, next;
    private final DayCell[] cells = new DayCell[42];
    private YearMonth month = YearMonth.now();
    private LocalDate selectedDate = LocalDate.now();
    private int inset, gridTop, gridBottom;

    public CalendarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mBackground = 0xfff7f9fc;
        previous = control("previous", "<", () -> changeMonth(-1));
        today = control("today", "Today", () -> {
            selectedDate = LocalDate.now();
            setDisplayedMonth(YearMonth.from(selectedDate));
        });
        next = control("next", ">", () -> changeMonth(1));
        for (int i = 0; i < cells.length; i++) {
            final DayCell cell = new DayCell(context);
            cell.setId("day_" + i);
            cell.setOnClickListener(view -> {
                if (cell.date != null) { selectedDate = cell.date; invalidate(); }
            });
            cells[i] = cell;
            addView(cell, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
        updateDates();
    }
    private Button control(String id, String text, Runnable action) {
        Button button = new Button(getContext());
        button.setId(id); button.setText(text);
        button.setOnClickListener(view -> action.run());
        addView(button, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return button;
    }
    public YearMonth getDisplayedMonth() { return month; }
    public LocalDate getSelectedDate() { return selectedDate; }
    public void setDisplayedMonth(YearMonth value) {
        if (value == null || value.getYear() < 1 || value.getYear() > 9999)
            throw new IllegalArgumentException("Calendar year must be between 1 and 9999");
        month = value; updateDates(); invalidate();
    }
    private void changeMonth(int delta) {
        YearMonth value = month.plusMonths(delta);
        if (value.getYear() >= 1 && value.getYear() <= 9999) setDisplayedMonth(value);
    }
    private void updateDates() {
        int offset = month.atDay(1).getDayOfWeek().getValue() % 7;
        for (int i = 0; i < cells.length; i++) {
            int day = i - offset + 1;
            cells[i].date = day >= 1 && day <= month.lengthOfMonth() ? month.atDay(day) : null;
        }
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(600, widthSpec), resolveSize(800, heightSpec));
        int width = getMeasuredWidth(), height = getMeasuredHeight();
        inset = Math.min(24, width / 20);
        gridTop = Math.min(180, height * 2 / 5);
        gridBottom = Math.max(gridTop, height - Math.min(78, height / 6));
        int controlWidth = Math.max(0, (width - 2 * inset - 16) / 3);
        for (Button button : new Button[]{previous, today, next}) measureExact(button, controlWidth, 44);
        for (int i = 0; i < cells.length; i++) {
            int column = i % 7, row = i / 7;
            measureExact(cells[i], edge(column + 1) - edge(column), rowEdge(row + 1) - rowEdge(row));
        }
    }
    private static void measureExact(View view, int width, int height) {
        view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
    private int edge(int column) { return inset + column * Math.max(0, getMeasuredWidth() - 2 * inset) / 7; }
    private int rowEdge(int row) { return gridTop + row * (gridBottom - gridTop) / 6; }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int top = Math.max(0, gridTop - 100);
        Button[] buttons = {previous, today, next};
        for (int i = 0; i < buttons.length; i++) {
            Button button = buttons[i];
            int x = inset + i * (button.getMeasuredWidth() + 8);
            button.layout(x, top, x + button.getMeasuredWidth(), top + button.getMeasuredHeight());
        }
        for (int i = 0; i < cells.length; i++)
            cells[i].layout(edge(i % 7), rowEdge(i / 7), edge(i % 7 + 1), rowEdge(i / 7 + 1));
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawText("CALENDAR", inset, 28, 14, 0xff667085);
        canvas.drawText(month.format(DateTimeFormatter.ofPattern("yyyy-MM")), inset, 62, 28, 0xff182230);
        int labelSize = Math.max(9, Math.min(14, (getWidth() - 2 * inset) / 35));
        for (int i = 0; i < 7; i++)
            canvas.drawText(WEEKDAYS[i], edge(i) + 5, gridTop - 14, labelSize,
                    i == 0 || i == 6 ? 0xffcf5265 : 0xff667085);
        canvas.drawText("Selected: " + selectedDate, inset, gridBottom + 30, 16, 0xff344054);
        canvas.drawText("Blue: today", inset, gridBottom + 52, 12, 0xff526cba);
    }
    private final class DayCell extends View {
        private LocalDate date;
        DayCell(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            if (date == null) return;
            boolean isToday = date.equals(LocalDate.now());
            int background = isToday ? 0xff5179db : date.equals(selectedDate) ? 0xffdce7ff
                    : date.getDayOfWeek().getValue() >= 6 ? 0xfffff4f5 : 0xffffffff;
            canvas.fillRect(2, 2, getWidth() - 4, getHeight() - 4, isPressed() ? 0xffb9ccef : background);
            canvas.strokeRect(2, 2, getWidth() - 4, getHeight() - 4, 0xffdbe1ea);
            canvas.drawText(String.valueOf(date.getDayOfMonth()), 10, Math.min(32, getHeight() - 8),
                    Math.max(12, Math.min(22, getWidth() / 3)), isToday ? 0xffffffff : 0xff182230);
        }
    }
}
