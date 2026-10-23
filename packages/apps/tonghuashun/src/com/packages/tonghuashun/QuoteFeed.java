package com.packages.tonghuashun;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One bounded worker; the caller dispatches immutable results back to its UI Handler. */
public final class QuoteFeed implements AutoCloseable {
    interface Source { List<QuoteClient.Quote> fetch() throws IOException; }
    public static final class Result {
        public final List<QuoteClient.Quote> quotes;
        public final String error;
        Result(List<QuoteClient.Quote> quotes, String error) { this.quotes = Collections.unmodifiableList(new ArrayList<>(quotes)); this.error = error; }
    }
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "market-quotes"); t.setDaemon(true); return t; });
    private final Source source;
    private final Consumer<Result> listener;
    private final BooleanSupplier active;
    private final AtomicBoolean queued = new AtomicBoolean();
    private volatile boolean closed;
    public QuoteFeed(Consumer<Result> listener, BooleanSupplier active) { this(new QuoteClient()::fetch, listener, active); }
    QuoteFeed(Source source, Consumer<Result> listener, BooleanSupplier active) {
        this.source = source; this.listener = listener; this.active = active;
        worker.scheduleWithFixedDelay(() -> { if (!closed) { if (!active.getAsBoolean()) close(); else refresh(); } }, 5, 5, TimeUnit.SECONDS);
        refresh();
    }
    public void refresh() {
        if (closed || !queued.compareAndSet(false, true)) return;
        try { worker.execute(() -> {
            try {
                if (closed || !active.getAsBoolean()) { close(); return; }
                Result result;
                try { result = new Result(source.fetch(), ""); }
                catch (Exception e) { result = new Result(Collections.emptyList(), "行情连接失败，保留最近报价；点击重试"); }
                if (!closed) listener.accept(result);
            } finally { queued.set(false); }
        }); } catch (RejectedExecutionException ignored) { queued.set(false); }
    }
    public boolean isClosed() { return closed; }
    @Override public void close() { closed = true; worker.shutdownNow(); }
}
