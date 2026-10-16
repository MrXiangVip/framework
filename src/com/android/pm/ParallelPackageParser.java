package com.android.pm;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.*;

/** Workers parse; the PMS scan thread consumes results and registers packages. */
public class ParallelPackageParser {
    private final PackageParser2 mPackageParser;
    private final CompletionService<ParseResult> mResults;

    ParallelPackageParser(PackageParser2 packageParser, ExecutorService executorService) {
        mPackageParser = Objects.requireNonNull(packageParser, "packageParser");
        // Unbounded completions also work with a caller-runs executor.
        mResults = new ExecutorCompletionService<>(executorService);
    }
    public void submit(File scanFile, int parseFlags) {
        mResults.submit(() -> {
            ParseResult result = new ParseResult();
            result.scanFile = scanFile;
            try {
                result.parsedPackage = parsePackage(scanFile, parseFlags);
                if (result.parsedPackage == null) throw new IllegalArgumentException("Null parsed package");
            } catch (Exception e) {
                result.throwable = e;
            }
            return result;
        });
    }
    public ParseResult take() {
        try {
            return mResults.take().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for package parsing", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Package parser worker failed", e.getCause());
        }
    }
    protected ParsedPackage parsePackage(File scanFile, int parseFlags) {
        return mPackageParser.parsePackage(scanFile, parseFlags, true);
    }
    public static final class ParseResult {
        public ParsedPackage parsedPackage;
        public File scanFile;
        public Throwable throwable;
    }
}