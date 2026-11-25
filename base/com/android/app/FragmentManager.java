package com.android.app;

import com.android.os.Handler;
import com.android.os.Looper;
import com.android.view.LayoutInflater;
import com.android.view.View;
import com.android.view.ViewGroup;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Collections;
import java.util.List;

/** Activity-owned Fragment manager. No back stack or saved-state restoration yet. */
public final class FragmentManager {
    private final Activity host;
    private final Thread thread;
    private final Handler handler;
    private final List<Fragment> added = new ArrayList<>();
    private final Deque<FragmentTransaction> pending = new ArrayDeque<>();
    private enum HostState { PAUSED, RESUMED, DESTROYED }
    private HostState hostState = HostState.PAUSED;
    private int executionDepth, lifecycleVersion;
    private boolean scheduled, draining;
    private static final int MAX_ASYNC_BATCH = 32;
    private int nextId;

    FragmentManager(Activity host) {
        this.host = host;
        thread = Thread.currentThread();
        handler = Looper.myLooper() == null ? null : new Handler(message -> {
            scheduled = false;
            if (alive()) drainPending(MAX_ASYNC_BATCH);
            return true;
        });
    }
    private void checkThread() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("Fragment operations must run on the Activity UI thread");
    }
    private void checkReady() {
        checkThread();
        if (!alive()) throw new IllegalStateException("Activity is destroyed");
    }
    public FragmentTransaction beginTransaction() { checkReady(); return new FragmentTransaction(this); }
    public List<Fragment> getFragments() { checkThread(); return Collections.unmodifiableList(new ArrayList<>(added)); }
    public Fragment findFragmentById(int id) {
        checkThread();
        for (int i = added.size() - 1; i >= 0; i--) if (added.get(i).containerId == id) return added.get(i);
        return null;
    }
    public Fragment findFragmentByTag(String tag) {
        checkThread();
        for (int i = added.size() - 1; i >= 0; i--) if (tag != null && tag.equals(added.get(i).tag)) return added.get(i);
        return null;
    }
    int commit(FragmentTransaction transaction, boolean now) {
        checkReady();
        if (!now && handler == null) throw new IllegalStateException("commit() requires a prepared Looper; use commitNow()");
        if (now && executionDepth != 0) throw new IllegalStateException("FragmentManager is already executing callbacks");
        int id = nextId++;
        if (now) execute(transaction);
        else {
            pending.addLast(transaction);
            schedulePending();
        }
        return id;
    }
    private boolean alive() { return hostState != HostState.DESTROYED && !host.isDestroyed(); }
    private boolean active(Fragment f) { return alive() && f.manager == this && f.added; }
    private void schedulePending() {
        if (!scheduled && !draining && alive() && !pending.isEmpty()) {
            scheduled = true;
            handler.sendMessage(handler.obtainMessage(0, null));
        }
    }
    public boolean executePendingTransactions() {
        checkReady();
        // Synchronous draining handles the queue present at entry. Callback submissions wait
        // for another turn so a producer cannot indefinitely block the UI thread.
        return drainPending(pending.size());
    }
    private boolean drainPending(int limit) {
        if (executionDepth != 0 || draining) throw new IllegalStateException("FragmentManager is already executing callbacks");
        if (handler != null && scheduled) handler.removeMessages(0);
        scheduled = false;
        boolean changed = false;
        draining = true;
        try {
            int count = Math.min(limit, pending.size());
            while (count-- > 0 && alive() && !pending.isEmpty()) {
                changed = true;
                execute(pending.removeFirst());
            }
        } finally {
            draining = false;
            schedulePending();
        }
        return changed;
    }
    private void execute(FragmentTransaction transaction) {
        // Validate the whole batch against a simulated membership before changing any Views.
        List<Fragment> simulated = new ArrayList<>(added);
        java.util.Map<Fragment, Integer> containers = new java.util.IdentityHashMap<>();
        for (Fragment f : added) containers.put(f, f.containerId);
        for (FragmentTransaction.Op op : transaction.operations) {
            Fragment f = op.fragment;
            if (f.manager != null && f.manager != this) throw new IllegalStateException("Fragment belongs to another manager");
            if (op.type == FragmentTransaction.REMOVE) {
                simulated.remove(f);
            } else {
                View target = host.findViewById(op.containerId);
                if (!(target instanceof ViewGroup)) throw new IllegalArgumentException("Fragment container is not a ViewGroup: " + op.containerId);
                if (simulated.contains(f)) throw new IllegalStateException("Fragment is already added");
                if (op.type == FragmentTransaction.REPLACE) simulated.removeIf(old -> containers.get(old) == op.containerId);
                simulated.add(f);
                containers.put(f, op.containerId);
            }
        }
        executionDepth++;
        try {
            for (FragmentTransaction.Op op : transaction.operations) {
                if (!alive()) break;
                if (op.type == FragmentTransaction.REMOVE) remove(op.fragment);
                else {
                    if (op.type == FragmentTransaction.REPLACE) {
                        for (Fragment old : new ArrayList<>(added)) if (old.containerId == op.containerId) remove(old);
                    }
                    if (alive()) add(op);
                }
            }
        } finally { executionDepth--; }
    }
    private void add(FragmentTransaction.Op op) {
        Fragment f = op.fragment;
        View target = host.findViewById(op.containerId);
        if (!(target instanceof ViewGroup)) throw new IllegalArgumentException("Fragment container is not a ViewGroup: " + op.containerId);
        f.manager = this; f.activity = host; f.containerId = op.containerId; f.tag = op.tag;
        f.container = (ViewGroup) target;
        f.added = true;
        f.creating = true;
        added.add(f);
        try {
            f.state = Fragment.State.ATTACHED;
            f.onAttach(host);
            if (!active(f)) return;
            f.state = Fragment.State.CREATED;
            f.onCreate();
            if (!active(f)) return;
            View view = f.onCreateView(LayoutInflater.from(host), f.container);
            if (!active(f)) return;
            if (view != null) {
                if (view.getParent() != null) throw new IllegalStateException("onCreateView must return an unattached View");
                f.view = view;
                f.state = Fragment.State.VIEW_CREATED;
                ViewGroup.LayoutParams params = view.getLayoutParams();
                if (params == null) params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                f.container.addView(view, params);
                if (!active(f)) return;
                f.onViewCreated(view);
                if (!active(f)) return;
            }
            f.creating = false;
            if (hostState == HostState.RESUMED) resume(f);
        } catch (RuntimeException | Error failure) {
            try { remove(f); } catch (RuntimeException | Error cleanup) { appendFailure(failure, cleanup); }
            throw failure;
        } finally { f.creating = false; }
    }
    private void resume(Fragment f) {
        if (active(f) && !f.creating && !f.isResumed()) { f.state = Fragment.State.RESUMED; f.onResume(); }
    }
    private void pause(Fragment f) {
        if (f.isResumed()) {
            f.state = f.view == null ? Fragment.State.CREATED : Fragment.State.VIEW_CREATED;
            f.onPause();
        }
    }
    private static Throwable appendFailure(Throwable first, Throwable next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }
    private static Throwable cleanup(Throwable failure, Runnable action) {
        try { action.run(); }
        catch (RuntimeException | Error next) { return appendFailure(failure, next); }
        return failure;
    }
    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof Error) throw (Error) failure;
    }
    private void remove(Fragment f) {
        if (!added.remove(f)) return;
        f.added = false;
        Throwable failure = cleanup(null, () -> pause(f));
        View view = f.view;
        if (view != null) {
            failure = cleanup(failure, () -> {
                if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
            });
            failure = cleanup(failure, f::onDestroyView);
            f.view = null;
        }
        if (f.state.ordinal() >= Fragment.State.CREATED.ordinal()) {
            f.state = Fragment.State.ATTACHED;
            failure = cleanup(failure, f::onDestroy);
        }
        if (f.state == Fragment.State.ATTACHED) {
            f.state = Fragment.State.DETACHED;
            failure = cleanup(failure, f::onDetach);
        }
        f.activity = null; f.manager = null; f.container = null;
        rethrow(failure);
    }
    void dispatchResume() { dispatch(HostState.RESUMED); }
    void dispatchPause() { dispatch(HostState.PAUSED); }
    private void dispatch(HostState state) {
        checkThread();
        if (hostState == HostState.DESTROYED) return;
        hostState = state;
        int version = ++lifecycleVersion;
        executionDepth++;
        try {
            for (Fragment f : new ArrayList<>(added)) {
                if (version != lifecycleVersion || hostState == HostState.DESTROYED) break;
                if (f.manager != this || !f.added) continue;
                if (state == HostState.RESUMED) resume(f); else pause(f);
            }
        } finally { executionDepth--; }
    }
    void dispatchDestroy() {
        checkThread();
        if (hostState == HostState.DESTROYED) return;
        hostState = HostState.DESTROYED;
        lifecycleVersion++;
        pending.clear();
        if (handler != null && scheduled) handler.removeMessages(0);
        scheduled = false;
        executionDepth++;
        Throwable failure = null;
        try {
            for (Fragment f : new ArrayList<>(added)) failure = cleanup(failure, () -> remove(f));
        } finally { executionDepth--; }
        rethrow(failure);
    }
}
