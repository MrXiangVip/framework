package com.packages.tonghuashun;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.regex.*;

/** Public Tencent snapshots, not an exchange push feed. Parsing never executes response scripts. */
public final class QuoteClient {
    static final Map<String, String> SYMBOLS;
    static {
        Map<String, String> symbols = new LinkedHashMap<>();
        symbols.put("sh000001", "000001.SH"); symbols.put("hkHSI", "HSI"); symbols.put("us.IXIC", "IXIC");
        symbols.put("sz300750", "300750"); symbols.put("sh600519", "600519"); symbols.put("sh600036", "600036");
        symbols.put("sz002594", "002594"); symbols.put("sh601318", "601318"); symbols.put("sh511260", "511260");
        symbols.put("sh688825", "688825"); symbols.put("hk00746", "HK0746"); symbols.put("hk01398", "HK1398");
        symbols.put("hk00390", "HK0390"); symbols.put("usJPM", "JPM"); symbols.put("usQCOM", "QCOM");
        SYMBOLS = Collections.unmodifiableMap(symbols);
    }
    private static final Pattern ENTRY = Pattern.compile("v_([A-Za-z0-9_.]+)=\"([^\"]*)\";");
    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    public static final class Quote {
        public final String code, name, time;
        public final double price, previous;
        private final double[] bids, asks;
        private final long[] bidSizes, askSizes;
        public Quote(String code, String name, double price, double previous, String time, double[] bids, double[] asks, long[] bidSizes, long[] askSizes) {
            this.code = code; this.name = name; this.price = price; this.previous = previous; this.time = time;
            this.bids = bids.clone(); this.asks = asks.clone(); this.bidSizes = bidSizes.clone(); this.askSizes = askSizes.clone();
        }
        public double bid(int level) { return bids[level]; }
        public double ask(int level) { return asks[level]; }
        public long bidSize(int level) { return bidSizes[level]; }
        public long askSize(int level) { return askSizes[level]; }
    }
    public static boolean supports(String code) { return SYMBOLS.containsValue(code); }
    public List<Quote> fetch() throws IOException {
        URL url = new URL("https://qt.gtimg.cn/q=" + String.join(",", SYMBOLS.keySet()));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5000); connection.setReadTimeout(5000); connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "FrameworkMarket/1.0"); connection.setRequestProperty("Referer", "https://gu.qq.com/");
        connection.setRequestProperty("Accept-Encoding", "identity"); connection.setUseCaches(false);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("行情服务 HTTP " + connection.getResponseCode());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] chunk = new byte[8192]; int size;
                while ((size = input.read(chunk)) != -1) {
                    if (bytes.size() + size > 2 * 1024 * 1024) throw new IOException("行情响应过大");
                    bytes.write(chunk, 0, size);
                }
            }
            List<Quote> result = parse(new String(bytes.toByteArray(), Charset.forName("GB18030")));
            if (result.isEmpty()) throw new IOException("行情服务暂未返回有效报价");
            return result;
        } finally { connection.disconnect(); }
    }
    public static List<Quote> parse(String response) {
        Map<String, Quote> result = new LinkedHashMap<>(); Matcher matcher = ENTRY.matcher(response);
        while (matcher.find()) {
            String symbol = matcher.group(1), code = SYMBOLS.get(symbol);
            if (code == null) continue;
            String[] fields = matcher.group(2).split("~", -1);
            if (fields.length < 31 || fields[1].trim().isEmpty() || fields[1].length() > 80) continue;
            String identity = symbol.substring(2);
            if (!fields[2].equals(identity) && !(symbol.startsWith("us") && fields[2].startsWith(identity + "."))) continue;
            try {
                double price = Double.parseDouble(fields[3]), previous = Double.parseDouble(fields[4]);
                if (!Double.isFinite(price) || !Double.isFinite(previous) || price <= 0 || previous <= 0 || price > 10_000_000 || previous > 10_000_000) continue;
                String time = fields[30].trim();
                LocalDateTime date = time.matches("[0-9]{14}") ? LocalDateTime.parse(time, COMPACT) : LocalDateTime.parse(time.replace('/', '-'), FULL);
                double[] bids = new double[5], asks = new double[5]; long[] bidSizes = new long[5], askSizes = new long[5];
                for (int level = 0; level < 5; level++) {
                    bids[level] = depthPrice(fields[9 + level * 2]); asks[level] = depthPrice(fields[19 + level * 2]);
                    bidSizes[level] = depthSize(fields[10 + level * 2]); askSizes[level] = depthSize(fields[20 + level * 2]);
                }
                result.put(code, new Quote(code, fields[1].trim(), price, previous, date.format(FULL), bids, asks, bidSizes, askSizes));
            } catch (IllegalArgumentException | java.time.DateTimeException ignored) { /* Skip one bad symbol, retaining the valid batch. */ }
        }
        return new ArrayList<>(result.values());
    }
    private static double depthPrice(String text) {
        try { double v = Double.parseDouble(text); return Double.isFinite(v) && v > 0 && v <= 10_000_000 ? v : 0; }
        catch (NumberFormatException e) { return 0; }
    }
    private static long depthSize(String text) {
        try { double v = Double.parseDouble(text); return Double.isFinite(v) && v > 0 && v < 1e12 ? (long) v : 0; }
        catch (NumberFormatException e) { return 0; }
    }
}
