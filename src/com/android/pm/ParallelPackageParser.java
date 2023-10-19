package com.android.pm;

import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

public class ParallelPackageParser {
    private static final int QUEUE_CAPACITY = 30;

    private final PackageParser2 mPackageParser;

    private final ExecutorService mExecutorService;
    private volatile String mInterruptedInThread;
    private final BlockingQueue<ParseResult> mQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    ParallelPackageParser(PackageParser2 packageParser, ExecutorService executorService) {
        mPackageParser = packageParser;
        mExecutorService = executorService;
    }
    public void submit(File scanFile, int parseFlags) {
        mExecutorService.submit(() -> {
            ParseResult pr = new ParseResult();
            try {
                pr.scanFile = scanFile;
                pr.parsedPackage = parsePackage(scanFile, parseFlags);
            } catch (Throwable e) {
                pr.throwable = e;
            } finally {
            }
            try {
                mQueue.put(pr);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Propagate result to callers of take().
                // This is helpful to prevent main thread from getting stuck waiting on
                // ParallelPackageParser to finish in case of interruption
                mInterruptedInThread = Thread.currentThread().getName();
            }
        });
    }
    protected ParsedPackage parsePackage(File scanFile, int parseFlags) {
        return mPackageParser.parsePackage(scanFile, parseFlags, true);
    }
    static class ParseResult {

        ParsedPackage parsedPackage; // Parsed package
        File scanFile; // File that was parsed
        Throwable throwable; // Set if an error occurs during parsing

        @Override
        public String toString() {
            return "ParseResult{" +
                    "parsedPackage=" + parsedPackage +
                    ", scanFile=" + scanFile +
                    ", throwable=" + throwable +
                    '}';
        }
    }

}
