package com.packages.tonghuashun;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** UI-thread quote snapshots and a persistent paper account; never submits real orders. */
public final class MarketStore {
    public static final class Stock {
        public String name;
        public final String code, market;
        public double price, change;
        private QuoteClient.Quote quote;
        long receivedAt;
        public final int decimals;
        public final boolean tradable;
        Stock(String name, String code, String market, double price, double change, int decimals, boolean tradable) {
            this.name = name; this.code = code; this.market = market;
            this.price = price; this.change = change; this.decimals = decimals; this.tradable = tradable;
        }
        public QuoteClient.Quote getQuote() { return quote; }
        public double percent() { return change / (price - change) * 100; }
        public String priceText() { return String.format(Locale.ROOT, "%." + decimals + "f", price); }
        public long cents() { return Math.round(price * 100); }
    }
    public static final class Position {
        public final int quantity;
        public final long cost;
        Position(int quantity, long cost) { this.quantity = quantity; this.cost = cost; }
    }
    private final List<Stock> stocks = new ArrayList<>();
    private final LinkedHashSet<String> watch = new LinkedHashSet<>();
    private final Map<String, Position> positions = new LinkedHashMap<>();
    private final List<String> orders = new ArrayList<>();
    private final Path file;
    private long cash = 100_000_000L;
    private String warning = "";
    private boolean online;

    public MarketStore(Path file) {
        this.file = file;
        // Fixed sample snapshot, explicitly identified as non-live throughout the interface.
        add("恒生指数主连", "HSI999", "期指", 23110, 2, 2, false);
        add("富时A50期指", "CN0W", "期指", 12991, -48, 0, false);
        add("纳斯达克中国金龙", "HXC", "美股", 5472.330, -46.205, 3, false);
        add("美国10年期国债", "US10Y", "债券", 4.15160, -.00790, 5, false);
        add("十年国债ETF", "511260", "沪市", 135.356, -.059, 3, false);
        add("长鑫科技", "688825", "科创", 42.85, 3.15, 2, false);
        add("宁德时代", "300750", "深市", 246.80, 4.58, 2, true);
        add("摩根大通", "JPM", "美股", 331.5800, -4.9600, 4, false);
        add("高通", "QCOM", "美股", 175.9100, 3.0800, 4, false);
        add("理文化工", "HK0746", "港股", 4.300, 0, 3, false);
        add("工商银行", "HK1398", "港股", 7.240, -.110, 3, false);
        add("中国中铁", "HK0390", "港股", 3.075, .010, 3, false);
        add("贵州茅台", "600519", "沪市", 1456.00, 12.50, 2, true);
        add("招商银行", "600036", "沪市", 43.62, -.38, 2, true);
        add("比亚迪", "002594", "深市", 108.26, 2.14, 2, true);
        add("中国平安", "601318", "沪市", 56.18, .76, 2, true);
        add("上证指数", "000001.SH", "指数", 3690.48, 32.45, 2, false);
        add("恒生指数", "HSI", "指数", 23093.37, 158.69, 2, false);
        add("纳斯达克", "IXIC", "指数", 25955.340, -53.520, 3, false);
        for (int i = 0; i < 12; i++) watch.add(stocks.get(i).code);
        load();
    }
    private void add(String n, String c, String m, double p, double d, int digits, boolean trade) {
        stocks.add(new Stock(n, c, m, p, d, digits, trade));
    }
    public List<Stock> all() { return Collections.unmodifiableList(stocks); }
    public void setOnline(boolean enabled) { online = enabled; }
    public boolean isOnline() { return online; }
    public boolean available(Stock stock) { return !online || stock.quote != null; }
    public boolean fresh(Stock stock) { long age = System.currentTimeMillis() - stock.receivedAt; return !online || stock.quote != null && age >= 0 && age < 60_000; }
    public boolean unpricedHoldings() { for (Stock s : holdings()) if (!available(s)) return true; return false; }
    /** Called only on the UI thread; a whole batch becomes visible before the next draw. */
    public int applyQuotes(List<QuoteClient.Quote> batch, long receivedAt) {
        int count = 0;
        for (QuoteClient.Quote quote : batch) {
            Stock stock = find(quote.code);
            if (stock == null || !QuoteClient.supports(quote.code) || !Double.isFinite(quote.price) || !Double.isFinite(quote.previous)
                    || quote.price <= 0 || quote.previous <= 0 || quote.price > 10_000_000 || quote.previous > 10_000_000) continue;
            // Older cached/provider responses must not replace a newer market snapshot.
            if (stock.quote != null && quote.time.compareTo(stock.quote.time) < 0) continue;
            stock.quote = quote; stock.receivedAt = receivedAt;
            stock.name = quote.name; stock.price = quote.price; stock.change = quote.price - quote.previous; count++;
        }
        return count;
    }
    public Stock find(String code) { for (Stock s : stocks) if (s.code.equals(code)) return s; return null; }
    public boolean watched(String code) { return watch.contains(code); }
    public List<Stock> watchlist() {
        List<Stock> result = new ArrayList<>(); for (String code : watch) { Stock s = find(code); if (s != null) result.add(s); } return result;
    }
    public List<Stock> search(String query) {
        List<Stock> result = new ArrayList<>(); String q = query.trim().toLowerCase(Locale.ROOT);
        for (Stock s : stocks) if ((s.name + s.code + s.market).toLowerCase(Locale.ROOT).contains(q)) result.add(s);
        return result;
    }
    public void toggleWatch(String code) throws IOException {
        if (find(code) == null) throw new IllegalArgumentException("未知证券");
        LinkedHashSet<String> before = new LinkedHashSet<>(watch);
        if (!watch.remove(code)) watch.add(code);
        try { save(); } catch (IOException e) { watch.clear(); watch.addAll(before); throw e; }
    }
    public long cash() { return cash; }
    public String warning() { return warning; }
    public Position position(String code) { Position p = positions.get(code); return p == null ? new Position(0, 0) : p; }
    public List<Stock> holdings() { List<Stock> result = new ArrayList<>(); for (Stock s : stocks) if (position(s.code).quantity > 0) result.add(s); return result; }
    public List<String> orders() { return Collections.unmodifiableList(orders); }
    public long marketValue() { long total = 0; for (Stock s : stocks) total += s.cents() * position(s.code).quantity; return total; }
    public long profit() { long cost = 0; for (Position p : positions.values()) cost += p.cost; return marketValue() - cost; }

    public void trade(String code, boolean buy, int quantity) throws IOException {
        Stock s = find(code); trade(code, buy, quantity, s == null ? 0 : s.cents());
    }
    public void trade(String code, boolean buy, int quantity, long confirmedPrice) throws IOException {
        Stock s = find(code);
        if (s == null || !s.tradable) throw new IllegalArgumentException("模拟交易仅支持列表中的沪深 A 股");
        if (quantity <= 0 || quantity > 1_000_000 || quantity % 100 != 0) throw new IllegalArgumentException("数量须为 100 股的整数倍");
        if (!fresh(s)) throw new IllegalArgumentException("报价未更新或已过期，请刷新后再模拟下单");
        if (confirmedPrice != s.cents()) throw new IllegalArgumentException("报价已变化，请检查金额后再次确认");
        Position old = position(code); long amount = confirmedPrice * quantity;
        if (buy && cash < amount) throw new IllegalArgumentException("可用模拟资金不足");
        if (!buy && old.quantity < quantity) throw new IllegalArgumentException("可卖持仓不足");
        if (buy && (long) old.quantity + quantity > 1_000_000) throw new IllegalArgumentException("单只股票模拟持仓上限为 100 万股");
        long before = cash;
        // Amounts and cost basis use integer cents, avoiding floating-point account drift.
        cash += buy ? -amount : amount;
        int remaining = old.quantity + (buy ? quantity : -quantity);
        long cost = buy ? old.cost + amount : remaining == 0 ? 0 : old.cost - old.cost * quantity / old.quantity;
        positions.put(code, new Position(remaining, cost));
        String record = (buy ? "买入 " : "卖出 ") + s.name + "  " + quantity + "股  @ " + s.priceText();
        orders.add(0, record);
        String removed = orders.size() > 30 ? orders.remove(30) : null;
        try { save(); } catch (IOException e) {
            cash = before; positions.put(code, old); orders.remove(0); if (removed != null) orders.add(removed); throw e;
        }
    }
    private void load() {
        if (!Files.exists(file)) return;
        try (InputStream in = Files.newInputStream(file)) {
            Properties p = new Properties(); p.load(in);
            long savedCash = Long.parseLong(p.getProperty("cash", "100000000"));
            if (savedCash < 0 || savedCash > 1_000_000_000_000L) throw new IOException("Invalid account");
            Map<String, Position> saved = new LinkedHashMap<>();
            for (Stock s : stocks) {
                int quantity = Integer.parseInt(p.getProperty(s.code + ".quantity", "0"));
                long cost = Long.parseLong(p.getProperty(s.code + ".cost", "0"));
                if (quantity < 0 || quantity > 1_000_000 || quantity % 100 != 0 || cost < 0 || cost > 1_000_000_000_000L
                        || quantity == 0 && cost != 0 || quantity > 0 && !s.tradable) throw new IOException("Invalid position");
                if (quantity > 0) saved.put(s.code, new Position(quantity, cost));
            }
            if (p.containsKey("watch")) { watch.clear(); for (String code : p.getProperty("watch").split(",")) if (find(code) != null) watch.add(code); }
            cash = savedCash; positions.putAll(saved);
            for (int i = 0; i < 30; i++) { String order = p.getProperty("order." + i); if (order != null) orders.add(order); }
        } catch (IOException | IllegalArgumentException e) { warning = "本地账户读取失败，当前使用临时初始账户；请检查数据文件。"; }
    }
    private void save() throws IOException {
        // Do not overwrite an unreadable account with a newly initialized one.
        if (!warning.isEmpty()) throw new IOException("账户文件读取异常，请先备份并修复本地数据");
        Properties p = new Properties(); p.setProperty("cash", Long.toString(cash)); p.setProperty("watch", String.join(",", watch));
        for (Map.Entry<String, Position> entry : positions.entrySet()) {
            p.setProperty(entry.getKey() + ".quantity", Integer.toString(entry.getValue().quantity));
            p.setProperty(entry.getKey() + ".cost", Long.toString(entry.getValue().cost));
        }
        for (int i = 0; i < orders.size(); i++) p.setProperty("order." + i, orders.get(i));
        Path absolute = file.toAbsolutePath(); Files.createDirectories(absolute.getParent());
        Path temp = Files.createTempFile(absolute.getParent(), "account-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) { p.store(out, "Tonghuashun paper trading account v1"); }
            try { Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
