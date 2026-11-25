package com.android.app;

import java.util.ArrayList;
import java.util.List;

/** A single-use, ordered batch of Fragment operations. */
public final class FragmentTransaction {
    static final int ADD = 0, REMOVE = 1, REPLACE = 2;
    static final class Op {
        final int type, containerId;
        final Fragment fragment;
        final String tag;
        Op(int type, int containerId, Fragment fragment, String tag) {
            this.type = type; this.containerId = containerId; this.fragment = fragment; this.tag = tag;
        }
    }
    private final FragmentManager manager;
    final List<Op> operations = new ArrayList<>();
    private boolean committed;
    FragmentTransaction(FragmentManager manager) { this.manager = manager; }
    private FragmentTransaction operation(int type, int id, Fragment fragment, String tag) {
        if (committed) throw new IllegalStateException("Transaction already committed");
        if (fragment == null) throw new NullPointerException("fragment");
        if (type != REMOVE && (id == 0 || id == -1)) throw new IllegalArgumentException("Invalid container ID");
        operations.add(new Op(type, id, fragment, tag));
        return this;
    }
    public FragmentTransaction add(int id, Fragment fragment) { return add(id, fragment, null); }
    public FragmentTransaction add(int id, Fragment fragment, String tag) { return operation(ADD, id, fragment, tag); }
    public FragmentTransaction replace(int id, Fragment fragment) { return replace(id, fragment, null); }
    public FragmentTransaction replace(int id, Fragment fragment, String tag) { return operation(REPLACE, id, fragment, tag); }
    public FragmentTransaction remove(Fragment fragment) { return operation(REMOVE, 0, fragment, null); }
    public int commit() { return commit(false); }
    public void commitNow() { commit(true); }
    private int commit(boolean now) {
        if (committed) throw new IllegalStateException("Transaction already committed");
        committed = true;
        return manager.commit(this, now);
    }
}
