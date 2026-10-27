package com.packages.tonghuashun;

import com.android.content.Context;
import com.android.graphics.Canvas;
import com.android.util.AttributeSet;
import com.android.view.*;
import com.android.widget.EditText;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Reference-inspired quote terminal. Drawing and hit targets share logical coordinates. */
public final class MarketView extends ViewGroup implements AutoCloseable {
    private static final int RED = 0xf3313c, GREEN = 0x079c23, INK = 0x303335, MUTED = 0x979b9f, LINE = 0xeeeeef;
    private static final String[] NAV = {"首页", "行情", "自选", "交易", "资讯", "理财"};
    private final MarketStore store;
    private final MarketText fonts;
    private QuoteFeed feed;
    private volatile boolean closed;
    private String feedStatus = "正在连接行情…";
    private String lastReceived = "";
    private long orderPrice;
    private final com.android.os.Handler quoteUi = new com.android.os.Handler(message -> acceptQuotes((QuoteFeed.Result) message.obj));
    private boolean acceptQuotes(QuoteFeed.Result result) {
        if (closed) return true;
        if (!attached()) { close(); return true; }
        if (result.error.isEmpty()) {
            int count = store.applyQuotes(result.quotes, System.currentTimeMillis());
            if (orderOpen && orderPrice == 0 && selected != null && store.available(selected)) orderPrice = selected.cents();
            lastReceived = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            feedStatus = "腾讯报价 · " + count + "只 · " + lastReceived + " · 5s / F5";
        } else feedStatus = result.error;
        invalidate(); return true;
    }
    private final List<Target> targets = new ArrayList<>();
    private int page = 2, sortColumn = -1, sortDirection = -1, filter, period;
    private double scroll, maxScroll, downX, downY, lastY, crosshair = -1;
    private boolean searching, editing, dragging, pressed, orderBuy = true;
    private String query = "", notice = "", orderQuantity = "100", articleTitle = "", articleBody = "";
    private MarketStore.Stock selected;
    private boolean orderOpen;
    private final EditText searchInput, quantityInput;
    private boolean inputPointer;
    private Rectangle chart = new Rectangle();
    private final class Target {
        final Rectangle bounds; final Runnable action;
        Target(int x, int y, int w, int h, Runnable action) { bounds = new Rectangle(x, y, w, h); this.action = action; }
    }
    public MarketView(Context context, AttributeSet attrs) {
        super(context, attrs);
        fonts = new MarketText(context.getClassLoader());
        store = new MarketStore(Paths.get(System.getProperty("framework.tonghuashun.data",
                Paths.get(System.getProperty("user.home"), ".local", "share", "framework-tonghuashun", "account.properties").toString())));
        store.setOnline(Boolean.parseBoolean(System.getProperty("framework.tonghuashun.online", "true")));
        searchInput = textInput("search", "输入名称或代码", 40, false);
        quantityInput = textInput("quantity", "股数", 7, true);
        quantityInput.setSelectAllOnFocus(true); quantityInput.setText("100");
        searchInput.setOnTextChangedListener((view, value) -> { query = value; scroll = 0; invalidate(); });
        quantityInput.setOnTextChangedListener((view, value) -> { orderQuantity = value; invalidate(); });
        notice = store.warning();
    }
    private EditText textInput(String id, String hint, int limit, boolean numeric) {
        EditText field = new EditText(getContext()) {
            @Override protected int measureText(String value) { return fonts.width(value, mTextSize); }
            @Override protected void drawTextLine(Canvas canvas, String value, int x, int baseline, int color) {
                fonts.draw(canvas, value, x, baseline, mTextSize, color);
            }
            @Override public void replaceSelection(String value) {
                if (numeric && value != null && !value.isEmpty()) {
                    value = value.replaceAll("[^0-9]", "");
                    if (value.isEmpty()) return;
                }
                super.replaceSelection(value);
            }
        };
        field.setId(id); field.setSingleLine(); field.setMaxLength(limit); field.setHint(hint);
        addView(field, new LayoutParams(-2, -2)); return field;
    }
    private void layoutInput(EditText field, int x, int y, int width, int height, int fontSize) {
        int size = Math.max(1, (int) Math.round(fontSize * scale()));
        if (field.getTextSize() != size) field.setTextSize(size);
        int padding = Math.max(1, (int) Math.round(8 * scale()));
        if (field.getPadding() != padding) field.setPadding(padding);
        int l = (int) Math.round(left() + x * scale()), t = (int) Math.round(y * scale());
        int w = (int) Math.round(width * scale()), h = (int) Math.round(height * scale());
        field.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        field.layout(l, t, l + w, t + h);
    }
    private void layoutInputs() {
        if (searching && !orderOpen) layoutInput(searchInput, 20, 14, 437, 42, 19);
        else { searchInput.clearFocus(); searchInput.layout(0, 0, 0, 0); }
        if (orderOpen) layoutInput(quantityInput, 226, Math.max(72, (height() - 420) / 2) + 137, 221, 48, 24);
        else { quantityInput.clearFocus(); quantityInput.layout(0, 0, 0, 0); }
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) { layoutInputs(); }

    public MarketStore getStore() { return store; }
    public int getPage() { return page; }
    public String getSelectedCode() { return selected == null ? "" : selected.code; }
    public String getQuery() { return query; }
    public boolean isOrderOpen() { return orderOpen; }
    public String getNotice() { return notice; }
    public String getQuoteStatus() { return feedStatus; }
    public boolean isQuoteFeedClosed() { return feed == null || feed.isClosed(); }
    private boolean attached() { View root = this; while (root.getParent() instanceof View) root = (View) root.getParent(); return root.getParent() != null; }
    private void startQuotes() {
        if (feed == null && !closed && store.isOnline() && attached())
            feed = new QuoteFeed(result -> quoteUi.sendMessage(quoteUi.obtainMessage(0, result)), () -> !closed && attached());
    }
    private void refreshQuotes() {
        if (store.isOnline() && feed != null) { feedStatus = "正在刷新…"; feed.refresh(); invalidate(); }
    }
    private String price(MarketStore.Stock stock) { return store.available(stock) ? stock.priceText() : "--"; }
    private String quoteStamp(MarketStore.Stock stock) {
        if (!QuoteClient.supports(stock.code)) return "未接入";
        if (stock.getQuote() == null) return "等待报价";
        return stock.getQuote().time.substring(5) + " · " + (stock.market.equals("美股") || stock.code.equals("IXIC") ? "美东" : "沪港");
    }
    @Override public void close() { closed = true; if (feed != null) feed.close(); }
    private double scale() { return Math.max(.01, Math.min(Math.min(getWidth() / 550.0, getHeight() / 650.0), 1.35)); }
    private double left() { return (getWidth() - 550 * scale()) / 2; }
    private int height() { return (int) Math.ceil(getHeight() / scale()); }
    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(resolveSize(600, w), resolveSize(900, h)); }
    private void target(int x, int y, int w, int h, Runnable action) { targets.add(new Target(x, y, w, h, action)); }
    private void navigate(int next) {
        page = next; selected = null; searching = editing = orderOpen = false; clearFocus();
        scroll = maxScroll = 0; crosshair = -1; articleTitle = ""; notice = store.warning(); invalidate();
    }
    private void detail(MarketStore.Stock stock) { selected = stock; searching = false; scroll = 0; crosshair = -1; period = 0; notice = ""; invalidate(); }
    private void search() { searching = true; selected = null; searchInput.setText(""); searchInput.requestFocus(); scroll = 0; notice = ""; invalidate(); }
    private void toggle(String code) {
        try { store.toggleWatch(code); notice = store.watched(code) ? "已加入自选" : "已移出自选"; }
        catch (Exception e) { notice = "保存失败：" + e.getMessage(); }
        invalidate();
    }
    private void order(boolean buy) {
        if (selected == null || !selected.tradable) { notice = "模拟买卖支持宁德时代、贵州茅台、招商银行、比亚迪、中国平安"; invalidate(); return; }
        orderPrice = store.available(selected) ? selected.cents() : 0; orderBuy = buy; orderOpen = true; quantityInput.clearFocus(); quantityInput.setText("100"); notice = ""; invalidate();
    }
    private void submit() {
        try {
            store.trade(selected.code, orderBuy, Integer.parseInt(orderQuantity), orderPrice);
            orderOpen = false; quantityInput.clearFocus(); notice = "模拟" + (orderBuy ? "买入" : "卖出") + "已成交，持仓与资金已保存";
        } catch (Exception e) { notice = e instanceof NumberFormatException ? "请输入有效的整数股数" : e.getMessage(); if (selected != null && e.getMessage() != null && e.getMessage().contains("报价已变化")) orderPrice = selected.cents(); }
        invalidate();
    }
    private List<MarketStore.Stock> rows() {
        List<MarketStore.Stock> list = searching ? store.search(query) : page == 2 ? store.watchlist() : new ArrayList<>(store.all());
        if (!searching && filter != 0) list.removeIf(s -> filter == 1 ? !s.market.equals("沪市") && !s.market.equals("深市") && !s.market.equals("科创")
                : filter == 2 ? !s.market.equals("港股") : !s.market.equals("美股"));
        if (sortColumn >= 0 && !searching) {
            Comparator<MarketStore.Stock> c = Comparator.comparingDouble(s -> sortColumn == 0 ? s.price : sortColumn == 1 ? s.percent() : s.change);
            Comparator<MarketStore.Stock> direction = sortDirection < 0 ? c.reversed() : c;
            list.sort((a, b) -> store.available(a) != store.available(b) ? store.available(a) ? -1 : 1 : store.available(a) ? direction.compare(a, b) : 0);
        }
        return list;
    }
    private void sort(int column) { if (sortColumn == column) sortDirection = -sortDirection; else { sortColumn = column; sortDirection = -1; } scroll = 0; invalidate(); }
    private static int color(double value) { return value > 0 ? RED : value < 0 ? GREEN : INK; }
    private static String number(double value, int decimals) { return String.format(Locale.ROOT, "%." + decimals + "f", value); }
    private static String signed(double value, int decimals) { return (value > 0 ? "+" : "") + number(value, decimals); }
    private static String money(long cents) { return String.format(Locale.ROOT, "%,.2f", cents / 100.0); }
    private void text(Graphics2D g, String s, int x, int y, int size, int color) { fonts.draw(g, s, x, y, size, color, false); }
    private void bold(Graphics2D g, String s, int x, int y, int size, int color) { fonts.draw(g, s, x, y, size, color, true); }
    private void right(Graphics2D g, String s, int x, int y, int size, int color) { text(g, s, x - fonts.width(g, s, size), y, size, color); }
    private void center(Graphics2D g, String s, int x, int y, int size, int color) { text(g, s, x - fonts.width(g, s, size) / 2, y, size, color); }
    private void fill(Graphics2D g, int color, int x, int y, int w, int h) { g.setColor(new Color(color)); g.fillRect(x, y, w, Math.max(0, h)); }
    private void line(Graphics2D g, int x1, int y1, int x2, int y2, int color) { g.setColor(new Color(color)); g.drawLine(x1, y1, x2, y2); }
    private void pill(Graphics2D g, String label, int x, int y, int w, boolean active, Runnable action) {
        g.setColor(new Color(active ? 0xffeded : 0xf3f4f5)); g.fillRoundRect(x, y, w, 32, 10, 10);
        center(g, label, x + w / 2, y + 22, 14, active ? RED : INK); target(x, y, w, 32, action);
    }
    @Override protected void onDraw(Canvas canvas) {
        layoutInputs();
        startQuotes();
        Graphics2D g = canvas.getImage().createGraphics();
        try {
            fill(g, 0xe9edf1, 0, 0, getWidth(), getHeight());
            g.translate(left(), 0); g.scale(scale(), scale()); g.clipRect(0, 0, 550, height());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(1.3f)); targets.clear(); chart = new Rectangle(); maxScroll = 0;
            fill(g, 0xffffff, 0, 0, 550, height());
            if (searching) drawSearch(g);
            else if (selected != null) drawDetail(g);
            else {
                header(g);
                if (page == 1 || page == 2) drawQuotes(g);
                else if (page == 0) drawHome(g);
                else if (page == 3 || page == 5) drawAccount(g);
                else drawInfo(g);
            }
            bottom(g);
            if (!notice.isEmpty() && !orderOpen) {
                fill(g, 0xfff4e6, 0, height() - 104, 550, 30);
                text(g, clipped(g, notice, 520, 12), 15, height() - 84, 12, 0x98661e);
            }
            if (!articleTitle.isEmpty()) drawArticle(g);
            if (orderOpen) drawOrder(g);
        } finally { g.dispose(); }
    }
    private String clipped(Graphics2D g, String value, int width, int size) {
        if (fonts.width(g, value, size) <= width) return value;
        int end = value.length(); while (end > 0 && fonts.width(g, value.substring(0, end) + "…", size) > width) end = value.offsetByCodePoints(end, -1);
        return value.substring(0, end) + "…";
    }
    private void header(Graphics2D g) {
        fill(g, RED, 0, 0, 550, 69);
        for (int i = 0; i < 3; i++) line(g, 20, 23 + i * 10, 50, 23 + i * 10, 0xffffff);
        target(0, 0, 70, 69, () -> navigate(5));
        center(g, page == 2 ? "同花顺自选" : page == 3 ? "模拟交易" : page == 5 ? "我的资产" : NAV[page], 275, 43, 24, 0xffffff);
        g.setColor(new Color(0xffffff)); g.drawRoundRect(414, 22, 48, 27, 10, 10);
        center(g, "模拟", 438, 41, 13, 0xffffff); target(400, 0, 70, 69, () -> navigate(3));
        g.drawOval(493, 20, 24, 24); line(g, 514, 42, 524, 52, 0xffffff); target(475, 0, 75, 69, this::search);
    }
    private void indices(Graphics2D g) {
        String[] names = {"上证指数", "恒生指数", "纳斯达克"};
        String[] codes = {"000001.SH", "HSI", "IXIC"};

        for (int i = 0; i < 3; i++) {
            MarketStore.Stock stock = store.find(codes[i]);
            int x = 66 + i * 127, c = store.available(stock) ? color(stock.change) : MUTED;
            center(g, names[i], x, 103, 18, INK); center(g, price(stock), x, 134, 23, c); center(g, store.available(stock) ? number(stock.change, 2) + "  " + number(stock.percent(), 2) + "%" : "待更新", x, 160, 15, c);
            final int index = i; target(i * 127, 70, 127, 105, () -> detail(store.find(new String[]{"000001.SH", "HSI", "IXIC"}[index])));
        }
        icon(g, 432, 108, 0, INK); center(g, "资金", 432, 153, 19, INK); target(390, 70, 85, 105, () -> navigate(5));
        icon(g, 509, 108, 4, INK); center(g, "资讯", 509, 153, 19, INK); target(475, 70, 75, 105, () -> navigate(4));
        fill(g, 0xf5f5f5, 0, 178, 550, 6);
        text(g, clipped(g, store.isOnline() ? feedStatus : "示例行情 · 非实时", 365, 12), 22, 203, 12, MUTED);
        target(0, 184, 390, 30, this::refreshQuotes); right(g, "本地自选  " + store.watchlist().size() + " 只", 528, 203, 12, MUTED);
    }
    private void drawQuotes(Graphics2D g) {
        indices(g);
        if (page == 1) {
            String[] filters = {"全部", "沪深", "港股", "美股"};
            for (int i = 0; i < 4; i++) { final int f = i; pill(g, filters[i], 22 + i * 130, 215, 116, filter == i, () -> { filter = f; scroll = 0; invalidate(); }); }
        }
        int top = page == 1 ? 257 : 216;
        text(g, editing ? "完成" : "编辑", 23, top + 27, 16, editing ? RED : MUTED); target(0, top, 75, 43, () -> { editing = !editing; invalidate(); });
        text(g, page == 2 ? "自选" : "证券", 85, top + 27, 16, MUTED);
        String[] labels = {"最新", "涨幅", "涨跌"}; int[] xs = {260, 391, 526};
        for (int i = 0; i < 3; i++) { final int col = i; right(g, labels[i] + (sortColumn == i ? sortDirection < 0 ? " ↓" : " ↑" : ""), xs[i], top + 27, 18, sortColumn == i ? RED : 0x777777); target(174 + i * 125, top, 125, 43, () -> sort(col)); }
        line(g, 0, top + 43, 550, top + 43, LINE);
        quoteList(g, rows(), top + 44, height() - 74, false);
    }
    private void quoteList(Graphics2D g, List<MarketStore.Stock> list, int top, int bottom, boolean searchResults) {
        int rowHeight = 68;
        maxScroll = Math.max(0, list.size() * rowHeight - Math.max(0, bottom - top)); scroll = Math.max(0, Math.min(scroll, maxScroll));
        Graphics2D body = (Graphics2D) g.create(); body.clipRect(0, top, 550, Math.max(0, bottom - top));
        try {
            if (list.isEmpty()) { center(body, searching ? "没有找到匹配的证券" : "暂无自选，点击右上角搜索添加", 275, top + 90, 20, MUTED); return; }
            for (int i = 0; i < list.size(); i++) {
                MarketStore.Stock s = list.get(i); int y = top + i * rowHeight - (int) scroll;
                if (y + rowHeight <= top || y >= bottom) continue;
                fill(body, !searchResults && i < 5 ? 0xf3f3f3 : 0xffffff, 0, y, 550, rowHeight);
                int nameX = editing && !searchResults ? 48 : 23;
                text(body, clipped(body, s.name, searchResults ? 300 : 126, s.name.length() > 5 ? 20 : 22), nameX, y + 30, s.name.length() > 5 ? 20 : 22, INK);
                int badgeColor = s.market.equals("美股") ? 0xd26a99 : s.market.equals("港股") ? 0x5c90ce : 0xd29a5b;
                fill(body, badgeColor, nameX, y + 43, 23, 16); center(body, s.market.equals("美股") ? "US" : s.market.equals("港股") ? "HK" : s.market.equals("期指") ? "期" : "沪深", nameX + 11, y + 55, 10, 0xffffff);
                text(body, s.code, nameX + 29, y + 56, 15, MUTED);
                if (searchResults) {
                    right(body, price(s), 458, y + 34, 23, (store.available(s) ? color(s.change) : MUTED));
                    center(body, store.watched(s.code) ? "✓" : "+", 510, y + 38, 29, store.watched(s.code) ? MUTED : RED);
                } else {
                    right(body, price(s), 260, y + 40, 25, (store.available(s) ? color(s.change) : MUTED));
                    right(body, (store.available(s) ? number(s.percent(), 2) + "%" : "--"), 391, y + 40, 25, (store.available(s) ? color(s.change) : MUTED));
                    right(body, (store.available(s) ? number(s.change, s.decimals) : "--"), 526, y + 40, 25, (store.available(s) ? color(s.change) : MUTED));
                }
                if (!searchResults && store.isOnline()) right(body, quoteStamp(s), 526, y + 57, 10, MUTED);
                line(body, 23, y + rowHeight - 1, 550, y + rowHeight - 1, LINE);
                int hitTop = Math.max(top, y), hitBottom = Math.min(bottom, y + rowHeight);
                if (searchResults) target(480, hitTop, 70, hitBottom - hitTop, () -> toggle(s.code));
                if (editing && !searchResults) {
                    body.setColor(new Color(RED)); body.fillOval(15, y + 23, 22, 22); line(body, 20, y + 34, 32, y + 34, 0xffffff);
                    target(0, hitTop, 47, hitBottom - hitTop, () -> toggle(s.code));
                }
                target(nameX, hitTop, (searchResults ? 475 : 550) - nameX, hitBottom - hitTop, () -> detail(s));
            }
        } finally { body.dispose(); }
        scrollbar(g, top, bottom);
    }
    private void scrollbar(Graphics2D g, int top, int bottom) {
        if (maxScroll <= 0) return;
        int track = Math.max(1, bottom - top), thumb = Math.max(30, (int) (track * track / (track + maxScroll)));
        fill(g, 0xc9cccf, 543, top + (int) ((track - thumb) * scroll / maxScroll), 4, thumb);
    }
    private void drawSearch(Graphics2D g) {
        fill(g, RED, 0, 0, 550, 69); fill(g, 0xffffff, 20, 14, 437, 42);
        center(g, "取消", 504, 44, 18, 0xffffff); target(465, 0, 85, 69, () -> navigate(page));
        text(g, "搜索证券 · 键盘输入 / 回车查看", 23, 102, 14, MUTED);
        quoteList(g, store.search(query), 123, height() - 74, true);
    }
    private void drawDetail(Graphics2D g) {
        MarketStore.Stock s = selected; int c = (store.available(s) ? color(s.change) : MUTED);
        fill(g, RED, 0, 0, 550, 69); text(g, "‹", 23, 48, 38, 0xffffff); target(0, 0, 65, 69, () -> { selected = null; scroll = 0; invalidate(); });
        center(g, s.name, 275, 33, 22, 0xffffff); center(g, s.market + "  " + s.code, 275, 56, 12, 0xffdddd);
        right(g, store.watched(s.code) ? "已自选" : "+自选", 529, 43, 17, 0xffffff); target(450, 0, 100, 69, () -> toggle(s.code));
        bold(g, price(s), 23, 129, 43, c); text(g, store.available(s) ? signed(s.change, s.decimals) + "    " + signed(s.percent(), 2) + "%" : "等待有效行情", 24, 161, 20, c);
        right(g, store.isOnline() ? quoteStamp(s) : "示例行情 · 非实时", 526, 101, 12, MUTED);
        right(g, "昨收  " + (store.available(s) ? number(s.price - s.change, s.decimals) : "--"), 526, 135, 15, INK);
        right(g, "所属市场  " + s.market, 526, 162, 15, MUTED);
        fill(g, 0xf7f7f8, 0, 182, 550, 7);
        String[] periods = {"分时", "日K", "周K", "月K"};
        for (int i = 0; i < 4; i++) { final int p = i; center(g, periods[i], 68 + i * 137, 219, 18, period == i ? RED : INK); target(i * 137, 189, 137, 43, () -> { period = p; crosshair = -1; invalidate(); }); if (period == i) fill(g, RED, 40 + i * 137, 229, 56, 3); }
        int chartBottom = Math.min(height() - 340, 568); chartBottom = Math.max(310, chartBottom);
        chart = new Rectangle(23, 254, 462, chartBottom - 254);
        text(g, "合成图表示意 · 非实时分时 / 历史 K 线", 23, 248, 10, MUTED);
        if (store.available(s)) drawChart(g, s, chart);
        else center(g, "等待报价，当前品种可能未接入", 275, chart.y + chart.height / 2, 16, MUTED);
        int y = chartBottom + 38;
        text(g, "五档盘口", 23, y, 17, INK); right(g, store.isOnline() ? "公开报价 / 量为源单位" : "示意数据", 526, y, 12, MUTED);
        for (int i = 0; i < 5; i++) {
            int yy = y + 28 + i * 24, askLevel = 4 - i;
            double ask = store.isOnline() ? s.getQuote() == null ? 0 : s.getQuote().ask(askLevel) : s.price * (1 + (.005 - i * .001));
            double bid = store.isOnline() ? s.getQuote() == null ? 0 : s.getQuote().bid(i) : s.price * (1 - (.001 + i * .001));
            text(g, "卖" + (5 - i), 23, yy, 14, MUTED); text(g, ask > 0 ? number(ask, s.decimals) : "--", 79, yy, 15, RED);
            right(g, store.isOnline() ? s.getQuote() != null && ask > 0 ? Long.toString(s.getQuote().askSize(askLevel)) : "--" : Integer.toString(260 + i * 178), 231, yy, 14, MUTED);
            text(g, "买" + (i + 1), 290, yy, 14, MUTED); text(g, bid > 0 ? number(bid, s.decimals) : "--", 345, yy, 15, GREEN);
            right(g, store.isOnline() ? s.getQuote() != null && bid > 0 ? Long.toString(s.getQuote().bidSize(i)) : "--" : Integer.toString(428 + i * 136), 526, yy, 14, MUTED);
        }
        int buyTop = height() - 130;
        fill(g, 0xffffff, 0, buyTop - 22, 550, 78); center(g, store.isOnline() ? "腾讯公开报价可能延时 · 图表为合成示意" : "示例走势 / 盘口，非历史行情", 275, buyTop - 5, 11, MUTED);
        pill(g, "模拟买入", 23, buyTop, 245, true, () -> order(true));
        pill(g, "模拟卖出", 283, buyTop, 244, false, () -> order(false));
    }
    private void drawChart(Graphics2D g, MarketStore.Stock stock, Rectangle r) {
        for (int i = 0; i <= 4; i++) {
            int y = r.y + i * r.height / 4; line(g, r.x, y, r.x + r.width, y, LINE);
            text(g, number(stock.price * (1.025 - i * .0125), Math.min(3, stock.decimals)), 490, y + 5, 11, MUTED);
            int x = r.x + i * r.width / 4; line(g, x, r.y, x, r.y + r.height, LINE);
        }
        Random random = new Random(stock.code.hashCode() + period * 37);
        int n = period == 0 ? 90 : 40; double value = .64;
        Path2D path = new Path2D.Double(); double[] points = new double[n];
        for (int i = 0; i < n; i++) {
            value = Math.max(.12, Math.min(.88, value + (random.nextDouble() - .47) * .14)); points[i] = value;
            double x = r.x + i * r.width / (double) (n - 1), y = r.y + value * r.height;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            if (period != 0) {
                double opening = Math.max(.05, Math.min(.95, value + (random.nextDouble() - .5) * .13));
                int c = opening >= value ? RED : GREEN, yy = (int) (r.y + Math.min(value, opening) * r.height);
                line(g, (int) x, yy - 7, (int) x, (int) (r.y + Math.max(value, opening) * r.height) + 7, c);
                fill(g, c, (int) x - 3, yy, 6, Math.max(2, (int) (Math.abs(opening - value) * r.height)));
            }
        }
        if (period == 0) {
            Path2D area = (Path2D) path.clone(); area.lineTo(r.x + r.width, r.y + r.height); area.lineTo(r.x, r.y + r.height); area.closePath();
            g.setPaint(new GradientPaint(0, r.y, new Color(0xdce8fa), 0, r.y + r.height, new Color(0xffffff))); g.fill(area);
            g.setColor(new Color(0x4a7fd5)); g.setStroke(new BasicStroke(1.7f)); g.draw(path);
            line(g, r.x, r.y + r.height / 2, r.x + r.width, r.y + r.height / 2, 0xe4b454);
        }
        text(g, period == 0 ? "09:30" : "示例区间起点", r.x, r.y + r.height + 23, 11, MUTED);
        center(g, period == 0 ? "11:30 / 13:00" : "合成 K 线", r.x + r.width / 2, r.y + r.height + 23, 11, MUTED);
        right(g, period == 0 ? "15:00" : "区间终点", r.x + r.width, r.y + r.height + 23, 11, MUTED);
        if (crosshair >= 0) {
            int i = Math.max(0, Math.min(n - 1, (int) (crosshair * (n - 1)))); int x = r.x + (int) (crosshair * r.width), y = r.y + (int) (points[i] * r.height);
            line(g, x, r.y, x, r.y + r.height, 0x858b94); line(g, r.x, y, r.x + r.width, y, 0x858b94);
            fill(g, 0x343c47, r.x, r.y, 195, 26); text(g, "示例价格 " + number(stock.price * (1.025 - points[i] * .05), stock.decimals), r.x + 8, r.y + 19, 13, 0xffffff);
        }
        g.setStroke(new BasicStroke(1.3f));
    }
    private void drawHome(Graphics2D g) {
        indices(g); bold(g, "市场温度", 23, 248, 22, INK); text(g, "离线示例 · 发现市场的不同切面", 23, 277, 14, MUTED);
        String[] sectors = {"半导体", "新能源", "银行", "人工智能", "消费", "保险"};
        String[] values = {"+3.26%", "+1.89%", "−0.48%", "+2.15%", "−0.76%", "+0.32%"};
        for (int i = 0; i < 6; i++) {
            int x = 23 + i % 3 * 176, y = 302 + i / 3 * 95; final int index = i;
            fill(g, i == 2 || i == 4 ? 0xedf8f0 : 0xfff0f0, x, y, 160, 81); text(g, sectors[i], x + 15, y + 29, 18, INK);
            text(g, values[i], x + 15, y + 62, 24, i == 2 || i == 4 ? GREEN : RED); target(x, y, 160, 81, () -> detail(store.find(new String[]{"688825", "300750", "600036", "QCOM", "600519", "601318"}[index])));
        }
        bold(g, "自选速览", 23, 533, 22, INK); right(g, "查看全部 ›", 526, 533, 15, RED); target(400, 505, 150, 43, () -> navigate(2));
        quoteList(g, store.watchlist(), 552, height() - 74, false);
    }
    private void drawAccount(Graphics2D g) {
        fill(g, 0xfff4f2, 0, 69, 550, 183); text(g, "模拟账户总资产（元）", 24, 107, 16, 0x94746e);
        bold(g, store.unpricedHoldings() ? "估值待更新" : money(store.cash() + store.marketValue()), 23, 157, 39, INK);
        text(g, "可用资金  " + money(store.cash()), 24, 199, 15, MUTED);
        right(g, "持仓盈亏  " + (store.unpricedHoldings() ? "--" : money(store.profit())), 526, 199, 15, color(store.profit()));
        text(g, (store.isOnline() ? "模拟账户 · 按联网报价成交 · 零手续费" : "初始资金 100 万 · 示例价格 · 零手续费模拟成交"), 24, 233, 12, MUTED);
        pill(g, "+ 模拟买入", 23, 271, 245, true, () -> { detail(store.find("300750")); order(true); });
        pill(g, "搜索证券", 283, 271, 244, false, this::search);
        List<MarketStore.Stock> holdings = store.holdings();
        int contentHeight = 46 + (holdings.isEmpty() ? 105 : holdings.size() * 70) + 45 + Math.max(1, store.orders().size()) * 37 + 20;
        maxScroll = Math.max(0, contentHeight - (height() - 74 - 320)); scroll = Math.max(0, Math.min(scroll, maxScroll));
        Graphics2D body = (Graphics2D) g.create(); body.clipRect(0, 320, 550, Math.max(0, height() - 74 - 320)); body.translate(0, -scroll);
        try {
            text(body, "我的持仓", 23, 341, 21, INK); right(body, "模拟账户 / CNY", 526, 341, 13, MUTED);
            int y = 366;
            if (holdings.isEmpty()) { fill(body, 0xf8f8f8, 23, y, 504, 85); center(body, "暂无持仓，开始一笔模拟交易", 275, y + 48, 17, MUTED); y += 105; }
            else for (MarketStore.Stock stock : holdings) {
                MarketStore.Position p = store.position(stock.code);
                text(body, stock.name, 23, y + 24, 20, INK); text(body, p.quantity + " 股  ·  成本 " + money(p.cost), 23, y + 49, 13, MUTED);
                right(body, store.available(stock) ? money(stock.cents() * p.quantity) : "待更新", 526, y + 24, 22, color(stock.cents() * p.quantity - p.cost));
                right(body, "查看 / 卖出 ›", 526, y + 49, 13, RED); line(body, 23, y + 64, 527, y + 64, LINE);
                int top = Math.max(320, y - (int) scroll), bottom = Math.min(height() - 74, y + 65 - (int) scroll);
                if (bottom > top) target(0, top, 550, bottom - top, () -> detail(stock)); y += 70;
            }
            text(body, "最近成交", 23, y + 26, 20, INK); y += 45;
            if (store.orders().isEmpty()) text(body, "成交记录会在这里显示", 23, y + 20, 15, MUTED);
            for (String record : store.orders()) { text(body, clipped(body, record, 504, 15), 23, y + 20, 15, INK); y += 37; }
        } finally { body.dispose(); }
        scrollbar(g, 320, height() - 74);
    }
    private void drawInfo(Graphics2D g) {
        text(g, "看盘课堂", 23, 117, 29, INK); text(g, "认识行情，熟悉交易工具", 23, 149, 16, MUTED);
        String[] titles = {"红涨绿跌：如何阅读自选列表", "分时与 K 线，记录不同的时间尺度", "自选管理：把关注的证券放在一起", "模拟交易：从第一笔 100 股开始", "关于此应用的行情数据"};
        String[] details = {"联网模式每 5 秒请求腾讯公开报价，可能延时。涨跌额为最新价减昨收，涨幅为涨跌额除以昨收；点击表头切换排序。", "分时查看日内形态，日 / 周 / 月 K 切换展示周期。本应用图表为合成示意，不是历史行情。", "点击右上角搜索名称或代码，点击加号加入自选。在自选页点击编辑即可移除证券。", "模拟账户初始资金 100 万元，仅支持内置沪深 A 股，100 股起，按确认报价模拟成交，手续费为零；报价发生变化需要再次确认。", "联网报价来自腾讯公开数据，每 5 秒刷新；时间按来源市场显示。未覆盖品种显示未接入。图表与板块仍为合成示意，买卖始终是本地模拟。"};
        maxScroll = Math.max(0, 185 + titles.length * 105 - (height() - 74));
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        Graphics2D body = (Graphics2D) g.create(); body.clipRect(0, 170, 550, Math.max(0, height() - 244));
        try {
        for (int i = 0; i < titles.length; i++) {
            int y = 185 + i * 105 - (int) scroll; final int index = i;
            if (y + 90 < 170 || y >= height() - 74) continue;
            fill(body, 0xf8f8f8, 23, y, 504, 89); text(body, "0" + (i + 1), 38, y + 31, 14, RED);
            text(body, titles[i], 77, y + 34, 18, INK); text(body, "操作指南    ·    点击查看", 77, y + 65, 13, MUTED);
            target(23, Math.max(170, y), 504, Math.min(height() - 74, y + 89) - Math.max(170, y), () -> { articleTitle = titles[index]; articleBody = details[index]; invalidate(); });
        }
        } finally { body.dispose(); }
        scrollbar(g, 170, height() - 74);
    }
    private void drawArticle(Graphics2D g) {
        targets.clear();
        g.setColor(new Color(0, 0, 0, 95)); g.fillRect(0, 0, 550, height());
        int top = Math.max(80, (height() - 300) / 2);
        fill(g, 0xffffff, 23, top, 504, 300);
        text(g, articleTitle, 44, top + 44, 20, INK);
        int y = top + 90; StringBuilder row = new StringBuilder();
        // Wrap by Unicode codepoint so the complete guide stays readable on narrow windows.
        for (int offset = 0; offset < articleBody.length();) {
            int cp = articleBody.codePointAt(offset); String ch = new String(Character.toChars(cp));
            if (fonts.width(g, row.toString() + ch, 17) > 460) { text(g, row.toString(), 44, y, 17, INK); y += 30; row.setLength(0); }
            row.append(ch); offset += Character.charCount(cp);
        }
        if (row.length() > 0) text(g, row.toString(), 44, y, 17, INK);
        pill(g, "知道了", 44, top + 245, 462, true, () -> { articleTitle = ""; invalidate(); });
    }
    private void bottom(Graphics2D g) {
        int y = height() - 74; fill(g, 0xfafafa, 0, y, 550, 74); line(g, 0, y, 550, y, LINE);
        for (int i = 0; i < NAV.length; i++) {
            final int index = i; int x = i * 550 / NAV.length, next = (i + 1) * 550 / NAV.length, c = page == i ? RED : INK;
            icon(g, (x + next) / 2, y + 27, i, c); center(g, NAV[i], (x + next) / 2, y + 64, 16, c);
            target(x, y, next - x, 74, () -> navigate(index));
        }
    }
    private void icon(Graphics2D g, int x, int y, int kind, int color) {
        g.setColor(new Color(color)); g.setStroke(new BasicStroke(1.8f));
        if (kind == 0) {
            g.drawOval(x - 13, y - 13, 26, 26); Path2D p = new Path2D.Double(); p.moveTo(x - 9, y); p.lineTo(x - 3, y); p.lineTo(x, y - 6); p.lineTo(x + 4, y + 5); p.lineTo(x + 7, y); g.draw(p);
        } else if (kind == 1) {
            g.drawRoundRect(x - 14, y - 12, 28, 24, 3, 3); Path2D p = new Path2D.Double(); p.moveTo(x - 9, y + 5); p.lineTo(x - 3, y - 3); p.lineTo(x + 2, y + 1); p.lineTo(x + 9, y - 7); g.draw(p);
        } else if (kind == 2) {
            g.fillOval(x - 6, y - 15, 12, 12); g.fillRoundRect(x - 12, y, 24, 14, 5, 5); g.setColor(new Color(0xffffff)); g.drawLine(x - 4, y + 7, x, y + 10); g.drawLine(x, y + 10, x + 7, y + 2);
        } else if (kind == 3) { g.drawOval(x - 14, y - 14, 28, 28); center(g, "¥", x, y + 8, 23, color); }
        else if (kind == 4) { g.drawRect(x - 10, y - 14, 20, 29); for (int i = 0; i < 3; i++) g.drawLine(x - 5, y - 7 + i * 7, x + 5, y - 7 + i * 7); }
        else { g.drawOval(x - 13, y - 11, 26, 26); g.drawLine(x - 6, y - 15, x + 6, y - 15); g.drawLine(x - 6, y - 15, x, y - 20); g.drawLine(x, y - 20, x + 6, y - 15); center(g, "¥", x, y + 9, 20, color); }
        g.setStroke(new BasicStroke(1.3f));
    }
    private void drawOrder(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 95)); g.fillRect(0, 0, 550, height());
        // Modal hit targets replace the underlying page; clicks cannot trade through the sheet.
        targets.clear(); int y = Math.max(72, (height() - 420) / 2);
        fill(g, 0xffffff, 20, y, 510, 416);
        text(g, orderBuy ? "模拟买入" : "模拟卖出", 43, y + 43, 25, orderBuy ? RED : GREEN);
        right(g, "关闭", 508, y + 40, 16, MUTED); target(450, y, 80, 60, () -> { orderOpen = false; quantityInput.clearFocus(); invalidate(); });
        text(g, selected.name + "  " + selected.code, 43, y + 85, 20, INK);
        text(g, (store.isOnline() ? "确认报价  " : "示例成交价  ") + (store.available(selected) && orderPrice > 0 ? number(orderPrice / 100.0, 2) : "--") + " 元", 43, y + 119, 16, MUTED);
        text(g, "数量（股）", 43, y + 167, 16, INK);
        fill(g, 0xf4f5f6, 166, y + 137, 341, 48);
        center(g, "−", 195, y + 170, 24, INK); center(g, "+", 479, y + 170, 24, INK);
        target(166, y + 137, 60, 48, () -> adjustQuantity(-100)); target(447, y + 137, 60, 48, () -> adjustQuantity(100));
        String amount = "—";
        try { int n = Integer.parseInt(orderQuantity); if (n > 0 && n <= 1_000_000 && orderPrice > 0 && store.available(selected)) amount = money(orderPrice * n); } catch (NumberFormatException ignored) { }
        text(g, "预计金额", 43, y + 221, 16, MUTED); right(g, amount + " 元", 507, y + 221, 22, INK);
        text(g, orderBuy ? "可用资金  " + money(store.cash()) : "可卖数量  " + store.position(selected.code).quantity + " 股", 43, y + 258, 15, MUTED);
        text(g, "100 股整数倍 · 零手续费 · 模拟成交", 43, y + 287, 13, MUTED);
        if (!notice.isEmpty()) text(g, clipped(g, notice, 464, 13), 43, y + 313, 13, RED);
        fill(g, orderBuy ? RED : GREEN, 43, y + 336, 464, 55); center(g, "确认模拟" + (orderBuy ? "买入" : "卖出"), 275, y + 372, 20, 0xffffff);
        target(43, y + 336, 464, 55, this::submit);
    }
    private void adjustQuantity(int delta) {
        try { quantityInput.setText(Integer.toString(Math.max(100, Math.min(1_000_000, Integer.parseInt(orderQuantity) + delta)))); }
        catch (NumberFormatException e) { quantityInput.setText("100"); }
        quantityInput.clearFocus(); invalidate();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getType() == KeyEvent.DOWN && e.getValue() == 0xffc2) { refreshQuotes(); return true; }
        if (e.getType() == KeyEvent.DOWN && e.getValue() == KeyEvent.ESCAPE) {
            if (orderOpen) orderOpen = false; else if (!articleTitle.isEmpty()) articleTitle = ""; else if (searching) searching = false; else selected = null;
            quantityInput.clearFocus(); notice = ""; invalidate(); return true;
        }
        if (e.getType() == KeyEvent.DOWN && (e.getModifiers() & KeyEvent.CONTROL) != 0 && (e.getValue() == 'f' || e.getValue() == 'F')) { if (!orderOpen && articleTitle.isEmpty()) search(); return true; }
        if (searching || orderOpen && quantityInput.isFocused()) {
            EditText field = searching ? searchInput : quantityInput;
            if (e.getType() == KeyEvent.DOWN && (e.getValue() == KeyEvent.ENTER || e.getValue() == 0xff8d)) {
                if (searching) { List<MarketStore.Stock> result = store.search(query); if (!result.isEmpty()) detail(result.get(0)); }
                else quantityInput.clearFocus();
                invalidate(); return true;
            }
            return field.dispatchKeyEvent(e);
        }
        if (orderOpen && e.getType() == KeyEvent.DOWN && (e.getValue() == KeyEvent.TAB || e.getValue() == KeyEvent.ISO_LEFT_TAB)) return quantityInput.requestFocus();
        if (!orderOpen && articleTitle.isEmpty() && selected == null && e.getType() == KeyEvent.DOWN && (e.getValue() == 0xff54 || e.getValue() == 0xff52)) {
            scroll = Math.max(0, Math.min(maxScroll, scroll + (e.getValue() == 0xff54 ? 70 : -70))); invalidate(); return true;
        }
        return false;
    }
    private boolean hitInput(EditText input, MotionEvent event) {
        return event.getX() >= input.getLeft() && event.getX() < input.getRight()
                && event.getY() >= input.getTop() && event.getY() < input.getBottom();
    }
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (inputPointer || e.getAction() == MotionEvent.ACTION_DOWN
                && (searchInput.getWidth() > 0 && hitInput(searchInput, e) || quantityInput.getWidth() > 0 && hitInput(quantityInput, e))) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) inputPointer = true;
            boolean handled = super.dispatchTouchEvent(e);
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) inputPointer = false;
            return handled;
        }

        double x = (e.getX() - left()) / scale(), y = e.getY() / scale();
        if (e.getAction() == MotionEvent.ACTION_CANCEL) { pressed = dragging = false; return true; }
        if (e.getAction() == MotionEvent.ACTION_SCROLL) {
            if (!orderOpen && articleTitle.isEmpty() && selected == null && y > 120 && y < height() - 74) { scroll = Math.max(0, Math.min(maxScroll, scroll - e.getScrollDelta() * .6)); invalidate(); }
            return true;
        }
        if (e.getButton() != 1) return false;
        if (e.getAction() == MotionEvent.ACTION_DOWN) { pressed = true; dragging = false; downX = x; downY = lastY = y; }
        if (e.getAction() == MotionEvent.ACTION_MOVE && pressed) {
            if (Math.abs(y - downY) > 7 || Math.abs(x - downX) > 7) dragging = true;
            if (!orderOpen && articleTitle.isEmpty() && selected == null && downY > (searching ? 123 : page == 1 ? 301 : page == 2 ? 260 : page == 3 || page == 5 ? 320 : page == 4 ? 170 : 552) && downY < height() - 74) scroll = Math.max(0, Math.min(maxScroll, scroll + lastY - y));
            lastY = y; invalidate();
        }
        if (!orderOpen && articleTitle.isEmpty() && selected != null && chart.contains(x, y)) { crosshair = Math.max(0, Math.min(1, (x - chart.x) / chart.width)); invalidate(); }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            boolean click = pressed && !dragging; pressed = false;
            if (click) for (int i = targets.size() - 1; i >= 0; i--) {
                Target t = targets.get(i); if (t.bounds.contains(x, y) && t.bounds.contains(downX, downY)) { t.action.run(); invalidate(); break; }
            }
        }
        return true;
    }
}
