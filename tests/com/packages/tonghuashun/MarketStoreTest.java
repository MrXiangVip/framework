package com.packages.tonghuashun;

import java.nio.file.*;
import java.util.Arrays;

/** Account arithmetic, rejected orders and on-disk persistence, independent of the UI. */
public final class MarketStoreTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path dir = Paths.get(args[0]); Files.createDirectories(dir); Path file = dir.resolve("account.properties");
        MarketStore s = new MarketStore(file);
        check(s.watchlist().size() == 12, "reference watchlist initialized");
        check(s.search("宁德").size() == 1 && s.search("300750").size() == 1, "Chinese name and code search");
        check(s.search("qcom").size() == 1, "case-insensitive symbol search");
        s.toggleWatch("300750"); check(!new MarketStore(file).watched("300750"), "watch removal persists");
        s.toggleWatch("300750"); check(new MarketStore(file).watched("300750"), "watch addition persists");
        long initial = s.cash(), amount = s.find("300750").cents() * 200;
        s.trade("300750", true, 200);
        check(s.cash() == initial - amount && s.position("300750").quantity == 200, "buy debits exact cents");
        check(s.cash() + s.marketValue() == initial && s.profit() == 0, "buy preserves account assets");
        s = new MarketStore(file); check(s.position("300750").cost == amount && s.orders().size() == 1, "account and order history reload");
        s.trade("300750", false, 100); check(s.position("300750").cost == amount / 2, "partial sale reduces average cost");
        final MarketStore account = s;
        reject(() -> account.trade("300750", false, 200), "oversell rejected");
        reject(() -> account.trade("600519", true, 1_000_000), "insufficient cash rejected");
        reject(() -> account.trade("JPM", true, 100), "unsupported currency/instrument rejected");
        reject(() -> account.trade("300750", true, -100), "negative quantity rejected");
        reject(() -> account.trade("300750", true, 101), "odd lot rejected");
        check(s.position("300750").quantity == 100 && s.orders().size() == 2, "rejected orders leave account unchanged");
        s.trade("300750", false, 100); check(s.cash() == initial && s.position("300750").cost == 0, "complete buy/sell round trip has no rounding loss");
        Files.write(file, Arrays.asList("cash=-1")); byte[] damaged = Files.readAllBytes(file);
        MarketStore broken = new MarketStore(file); check(!broken.warning().isEmpty(), "corrupt account reports recovery state");
        reject(() -> broken.trade("300750", true, 100), "corrupt account cannot be silently overwritten");
        check(Arrays.equals(damaged, Files.readAllBytes(file)) && broken.cash() == initial, "failed save rolls back account and preserves original file");
        Path directoryInsteadOfFile = dir.resolve("blocked"); Files.createDirectories(directoryInsteadOfFile);
        MarketStore blocked = new MarketStore(directoryInsteadOfFile);
        reject(() -> blocked.toggleWatch("300750"), "watch save failure reported");
        check(blocked.watched("300750"), "watch save failure rolls back membership");
        System.out.println("PASS: " + checks + " market/account checks");
    }
    private interface Action { void run() throws Exception; }
    private static void reject(Action action, String label) throws Exception { boolean rejected = false; try { action.run(); } catch (java.io.IOException | IllegalArgumentException e) { rejected = true; } check(rejected, label); }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
