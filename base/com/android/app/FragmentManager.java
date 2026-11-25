package com.android.app;

import com.android.os.Handler;
import com.android.os.Looper;
import com.android.view.LayoutInflater;
import com.android.view.View;
import com.android.view.ViewGroup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Activity-owned Fragment manager. No back stack or saved-state restoration yet. */
public final class FragmentManager {
    private final Activity host;
    private final Thread thread;
    private final Handler handler;
    private final List<Fragment> added = new ArrayList<>();
    private final List<FragmentTransaction> pending = new ArrayList<>();
    private boolean resumed, destroyed, executing;
    private int nextId;

    FragmentManager(Activity host) {
        this.host = host;
        thread = Thread.currentThread();
        handler = Looper.myLooper() == null ? null : new Handler(message -> {
            executePendingTransactions(); return true;
        });
    }
    private void checkThread() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("Fragment operations must run on the Activity UI thread");
    }
    private void checkReady() {
        checkThread();
        if (destroyed || host.isDestroyed()) throw new IllegalStateException("Activity is destroyed");
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
        if (now && executing) throw new IllegalStateException("FragmentManager is already executing callbacks");
        int id = nextId++;
        if (now) execute(transaction);
        else {
            pending.add(transaction);
            handler.sendMessage(handler.obtainMessage(0, null));
        }
        return id;
    }
    public boolean executePendingTransactions() {
        checkReady();
        if (executing) throw new IllegalStateException("FragmentManager is already executing callbacks");
        if (handler != null) handler.removeMessages(0);
        boolean changed = !pending.isEmpty();
        while (!pending.isEmpty()) execute(pending.remove(0));
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
        executing = true;
        try {
            for (FragmentTransaction.Op op : transaction.operations) {
                if (op.type == FragmentTransaction.REMOVE) remove(op.fragment);
                else {
                    if (op.type == FragmentTransaction.REPLACE) {
                        for (Fragment old : new ArrayList<>(added)) if (old.containerId == op.containerId) remove(old);
                    }
                    add(op);
                }
            }
        } finally { executing = false; }
    }
    private void add(FragmentTransaction.Op op) {
        Fragment f = op.fragment;
        f.manager = this; f.activity = host; f.containerId = op.containerId; f.tag = op.tag;
        f.container = (ViewGroup) host.findViewById(op.containerId);
        added.add(f);
        try {
            f.onAttach(host);
            f.onCreate();
            View view = f.onCreateView(LayoutInflater.from(host), f.container);
            if (view != null) {
                if (view.getParent() != null) throw new IllegalStateException("onCreateView must return an unattached View");
                f.view = view;
                ViewGroup.LayoutParams params = view.getLayoutParams();
                if (params == null) params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                f.container.addView(view, params);
                f.onViewCreated(view);
            }
            if (resumed) resume(f);
        } catch (RuntimeException | Error failure) {
            try { remove(f); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    private void resume(Fragment f) { if (!f.resumed) { f.resumed = true; f.onResume(); } }
    private void pause(Fragment f) { if (f.resumed) { f.resumed = false; f.onPause(); } }
    private void remove(Fragment f) {
        if (!added.remove(f)) return;
        try {
            pause(f);
        } finally {
            try {
                if (f.view != null) {
                    if (f.view.getParent() instanceof ViewGroup) ((ViewGroup) f.view.getParent()).removeView(f.view);
                    try { f.onDestroyView(); } finally { f.view = null; }
                }
            } finally {
                try { f.onDestroy(); }
                finally {
                    try { f.onDetach(); }
                    finally { f.activity = null; f.manager = null; f.container = null; }
                }
            }
        }
    }
    void dispatchResume() { dispatch(true); }
    void dispatchPause() { dispatch(false); }
    private void dispatch(boolean value) {
        checkThread();
        resumed = value;
        executing = true;
        try { for (Fragment f : new ArrayList<>(added)) { if (value) resume(f); else pause(f); } }
        finally { executing = false; }
    }
    void dispatchDestroy() {
        checkThread();
        destroyed = true; resumed = false; pending.clear();
        if (handler != null) handler.removeMessages(0);
        executing = true;
        RuntimeException failure = null;
        try {
            for (Fragment f : new ArrayList<>(added)) {
                try { remove(f); } catch (RuntimeException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
            }
        } finally { executing = false; }
        if (failure != null) throw failure;
    }
}
