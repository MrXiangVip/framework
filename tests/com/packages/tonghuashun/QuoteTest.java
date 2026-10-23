package com.packages.tonghuashun;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic response, batch update, price confirmation and worker lifecycle checks. */
public final class QuoteTest {
    private static int checks;
    private static String row(String symbol, String code, String price, String previous, String time) {
        String[] fields = new String[33]; Arrays.fill(fields, "0");
        fields[1] = "宁德时代"; fields[2] = code; fields[3] = price; fields[4] = previous;
        fields[9] = "237.59"; fields[10] = "53"; fields[19] = "237.61"; fields[20] = "3"; fields[30] = time;
        return "v_" + symbol + "=\"" + String.join("~", fields) + "\";\n";
    }
    private static QuoteClient.Quote quote(double price, String time) {
        return QuoteClient.parse(row("sz300750", "300750", Double.toString(price), "220.41", time)).get(0);
    }
    public static void main(String[] args) throws Exception {
        String valid = row("sz300750", "300750", "237.60", "220.41", "20261023161430");
        List<QuoteClient.Quote> batch = QuoteClient.parse(valid);
        check(batch.size() == 1 && batch.get(0).name.equals("宁德时代"), "Chinese quote parsed without script evaluation");
        QuoteClient.Quote q = batch.get(0);
        check(q.time.equals("2026-10-23 16:14:30") && q.price == 237.60 && q.previous == 220.41, "time and price fields interpreted");
        check(q.bid(0) == 237.59 && q.bidSize(0) == 53 && q.ask(0) == 237.61 && q.askSize(0) == 3, "bid/ask levels use actual feed fields");
        check(QuoteClient.parse(row("hk00746", "00746", "4.30", "4.30", "2026/10/23 16:08:14")).size() == 1, "Hong Kong timestamps supported");
        check(QuoteClient.parse(row("usJPM", "JPM.N", "331.58", "336.54", "2026-10-23 16:04:27")).size() == 1, "US timestamps and ticker suffix supported");
        for (String price : new String[]{"NaN", "Infinity", "-1", "0", "10000001"}) check(QuoteClient.parse(row("sz300750", "300750", price, "220", "20261023161430")).isEmpty(), "invalid quote price rejected: " + price);
        check(QuoteClient.parse(row("sz300750", "600519", "237.60", "220", "20261023161430")).isEmpty(), "mismatched security identity rejected");
        check(QuoteClient.parse(row("sz300750", "300750", "237.60", "220", "20260230161430")).isEmpty(), "invalid calendar date rejected");
        check(QuoteClient.parse("v_unknown=\"bad\";" + valid).size() == 1, "unsupported/malformed rows do not discard valid symbols");
        check(QuoteClient.parse(valid + valid).size() == 1, "duplicate symbol rows collapse to one snapshot");
        check(!QuoteClient.supports("HSI999") && QuoteClient.supports("000001.SH"), "coverage is explicit; futures are not substituted with indices");
        Path file = Paths.get(args[0]); Files.createDirectories(file.getParent()); MarketStore s = new MarketStore(file); s.setOnline(true);
        check(!s.available(s.find("300750")), "startup does not present fixtures as fetched quotes");
        reject(() -> s.trade("300750", true, 100), "unfetched sample cannot execute an online paper order");
        s.applyQuotes(batch, System.currentTimeMillis());
        check(s.available(s.find("300750")) && s.find("300750").price == 237.60, "fetched batch updates the existing selected stock instance");
        check(s.find("300750").change > 17.18 && s.find("300750").change < 17.20, "change computed from previous close");
        s.trade("300750", true, 100, 23760);
        check(s.cash() == 100_000_000L - 23760L * 100, "paper fill uses confirmed fetched price");
        s.applyQuotes(Collections.singletonList(quote(238, "20261023161431")), System.currentTimeMillis());
        reject(() -> s.trade("300750", false, 100, 23760), "quote change requires explicit re-confirmation");
        check(s.position("300750").quantity == 100, "rejected price change preserves holdings");
        s.applyQuotes(Collections.singletonList(quote(236, "20261023161429")), System.currentTimeMillis());
        check(s.find("300750").price == 238, "older provider snapshot cannot overwrite a newer one");
        s.applyQuotes(Collections.singletonList(quote(238, "20261023161431")), System.currentTimeMillis() - 61000);
        reject(() -> s.trade("300750", false, 100), "stale received quote cannot execute");
        AtomicInteger calls = new AtomicInteger(); CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        BlockingQueue<QuoteFeed.Result> results = new LinkedBlockingQueue<>();
        QuoteFeed feed = new QuoteFeed(() -> {
            calls.incrementAndGet(); entered.countDown();
            try { if (!release.await(2, TimeUnit.SECONDS)) throw new java.io.IOException("test timeout"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new java.io.IOException(e); }
            return batch;
        }, results::offer, () -> true);
        try {
            check(entered.await(2, TimeUnit.SECONDS), "network source runs off the caller thread");
            for (int i = 0; i < 30; i++) feed.refresh();
            check(calls.get() == 1, "rapid manual refresh coalesces into one in-flight request");
            release.countDown(); QuoteFeed.Result result = results.poll(2, TimeUnit.SECONDS);
            check(result != null && result.quotes.size() == 1 && result.error.isEmpty(), "worker publishes immutable valid batch");
        } finally { release.countDown(); feed.close(); }
        check(feed.isClosed(), "closing view feed stops scheduling");
        QuoteFeed failing = new QuoteFeed(() -> { throw new java.io.IOException("offline"); }, results::offer, () -> true);
        try { QuoteFeed.Result result = results.poll(2, TimeUnit.SECONDS); check(result != null && !result.error.isEmpty() && result.quotes.isEmpty(), "request failure reports stale-data status instead of fabricated prices"); }
        finally { failing.close(); }
        check(s.find("300750").price == 238, "failed network result retains previous quote");
        if (args.length > 1 && args[1].equals("--live")) {
            List<QuoteClient.Quote> live = new QuoteClient().fetch();
            check(live.stream().anyMatch(value -> value.code.equals("300750")), "actual HTTPS quote response contains mapped A-share");
            check(live.stream().anyMatch(value -> value.code.equals("HK0746")), "actual HTTPS quote response contains mapped HK share");
            check(live.stream().anyMatch(value -> value.code.equals("JPM")), "actual HTTPS quote response contains mapped US share");
            System.out.println("Live provider returned " + live.size() + " valid symbols; first market time=" + live.get(0).time);
        }
        System.out.println("PASS: " + checks + " quote parsing/update/worker checks");
    }
    private interface Action { void run() throws Exception; }
    private static void reject(Action action, String label) throws Exception { boolean rejected = false; try { action.run(); } catch (IllegalArgumentException e) { rejected = true; } check(rejected, label); }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
