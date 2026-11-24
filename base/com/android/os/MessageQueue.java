package com.android.os;

import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

/** Stable deadline ordering in the System.nanoTime() millisecond timebase. */
public final class MessageQueue extends AbstractQueue<Message> {
    private final LinkedList<Message> messages = new LinkedList<>();

    MessageQueue(boolean quitAllowed) {}

    Message next() {
        synchronized (this) {
            for (;;) {
                Message first = messages.peek();
                long remaining = first == null ? 0 : first.when - nowMillis();
                if (first != null && remaining <= 0) return messages.poll();
                try {
                    if (first == null) wait();
                    else wait(remaining);
                } catch (InterruptedException ignored) {
                    // Interrupts wake the queue; only due messages are dispatched.
                }
            }
        }
    }

    static long nowMillis() { return System.nanoTime() / 1_000_000L; }

    synchronized boolean enqueueMessage(Message msg, long when) {
        msg.when = when;
        int index = 0;
        while (index < messages.size() && messages.get(index).when <= when) index++;
        messages.add(index, msg);
        notifyAll();
        return true;
    }

    synchronized void removeMessages(Handler handler, int what) {
        messages.removeIf(message -> message.target == handler && message.what == what);
        notifyAll();
    }

    @Override public synchronized int size() { return messages.size(); }
    @Override public synchronized Message peek() { return messages.peek(); }
    @Override public synchronized Message poll() { return messages.poll(); }
    @Override public boolean offer(Message message) { return enqueueMessage(message, message.when); }
    @Override public synchronized Iterator<Message> iterator() {
        return new ArrayList<>(messages).iterator();
    }
    @Override public synchronized void clear() { messages.clear(); notifyAll(); }
}
