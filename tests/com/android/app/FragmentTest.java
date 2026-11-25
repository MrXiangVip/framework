package com.android.app;

import com.android.content.Context;
import com.android.os.Handler;
import com.android.os.Looper;
import com.android.res.Resources;
import com.android.view.*;
import com.android.widget.FrameLayout;
import java.net.URLClassLoader;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Headless lifecycle, transaction, input cleanup and APK resource integration checks. */
public final class FragmentTest {
    static class Host extends Activity {
        final Resources resources;
        final ClassLoader loader;
        final FrameLayout root;
        Host() { this(FragmentTest.class.getClassLoader()); }
        Host(ClassLoader loader) {
            this.loader = loader;
            resources = new Resources(loader);
            root = new FrameLayout(this);
            root.setId(100);
        }
        @Override public Resources getResources() { return resources; }
        @Override public ClassLoader getClassLoader() { return loader; }
        @Override public View findViewById(int id) { return root.findViewById(id); }
        // Deliberately omit super callbacks: dispatch belongs to perform* entry points.
        @Override protected void onResume() {}
        @Override protected void onPause() {}
        @Override protected void onDestroy() {}
    }
    static class Page extends Fragment {
        final List<String> events = new ArrayList<>();
        @Override public void onAttach(Context context) { events.add("attach"); }
        @Override public void onCreate() { events.add("create"); }
        @Override public View onCreateView(LayoutInflater inflater, ViewGroup container) {
            events.add("createView"); return new View(requireContext());
        }
        @Override public void onViewCreated(View view) { events.add("viewCreated"); }
        @Override public void onResume() { events.add("resume"); }
        @Override public void onPause() { events.add("pause"); }
        @Override public void onDestroyView() { events.add("destroyView"); }
        @Override public void onDestroy() { events.add("destroy"); }
        @Override public void onDetach() { events.add("detach"); }
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void expect(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); } catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Wrong exception", failure);
        }
        throw new AssertionError("Expected " + type.getName());
    }
    static void lifecycle() {
        Host host = new Host();
        FragmentManager manager = host.getFragmentManager();
        Page first = new Page();
        manager.beginTransaction().add(100, first, "first").commitNow();
        check(first.events.equals(Arrays.asList("attach", "create", "createView", "viewCreated")), "Initial lifecycle");
        check(first.getActivity() == host && first.requireView().getParent() == host.root, "Attachment");
        host.performResume(); host.performResume();
        host.onWindowMinimizedChanged(true); host.onWindowMinimizedChanged(false);
        check(first.events.equals(Arrays.asList("attach", "create", "createView", "viewCreated", "resume", "pause", "resume")), "Minimize/restore lifecycle");
        View oldView = first.getView();
        View unrelated = new View(host);
        host.root.addView(unrelated, new ViewGroup.LayoutParams(10, 10));
        Page second = new Page();
        manager.beginTransaction().replace(100, second, "second").commitNow();
        check(!first.isAdded() && first.getView() == null && oldView.getParent() == null, "Replacement cleanup");
        check(first.events.subList(7, first.events.size()).equals(Arrays.asList("pause", "destroyView", "destroy", "detach")), "Removal lifecycle");
        check(second.isResumed() && host.root.getChildCount() == 2 && unrelated.getParent() == host.root, "Replace preserves unrelated children");
        check(manager.findFragmentByTag("first") == null && manager.findFragmentById(100) == second, "Lookup");
        Page pending = new Page();
        manager.beginTransaction().add(100, pending).commit();
        host.performDestroy(); host.performDestroy();
        check(second.getView() == null && !second.isAdded() && pending.events.isEmpty(), "Destroy and pending cancellation");
        check(manager.getFragments().isEmpty() && host.root.getChildCount() == 1, "Manager cleanup");
        expect(IllegalStateException.class, () -> manager.beginTransaction());
        expect(IllegalStateException.class, () -> first.requireView());
        expect(IllegalStateException.class, () -> first.requireActivity());
        Host late = new Host(); late.performResume();
        Page latePage = new Page();
        late.getFragmentManager().beginTransaction().add(100, latePage).commitNow();
        check(latePage.isResumed(), "Manager created after resume"); late.performDestroy();
    }
    static void transactions() throws Exception {
        Host host = new Host(); FragmentManager manager = host.getFragmentManager();
        Page first = new Page(), second = new Page();
        manager.beginTransaction().add(100, first).replace(100, second, "second").commitNow();
        check(!first.isAdded() && second.isAdded() && host.root.getChildCount() == 1, "Ordered batch");
        Page invalid = new Page();
        expect(IllegalArgumentException.class, () -> manager.beginTransaction().remove(second).add(999, invalid).commitNow());
        check(second.isAdded() && invalid.events.isEmpty(), "Prevalidation must preserve old page");
        expect(IllegalStateException.class, () -> manager.beginTransaction().add(100, second).commitNow());
        Host other = new Host();
        expect(IllegalStateException.class, () -> other.getFragmentManager().beginTransaction().add(100, second).commitNow());
        FragmentTransaction transaction = manager.beginTransaction().remove(second);
        transaction.commitNow();
        expect(IllegalStateException.class, () -> transaction.commit());
        expect(IllegalStateException.class, () -> transaction.add(100, first));
        Page queued = new Page(); manager.beginTransaction().add(100, queued).commit();
        check(!queued.isAdded(), "commit must be asynchronous");
        check(manager.executePendingTransactions() && queued.isAdded(), "Pending execution");
        check(!manager.executePendingTransactions(), "Empty pending queue");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { manager.beginTransaction(); } catch (Throwable e) { failure.set(e); }
        });
        worker.start(); worker.join();
        check(failure.get() instanceof IllegalStateException, "Wrong-thread mutation");
        Host fresh = new Host(); failure.set(null);
        worker = new Thread(() -> { try { fresh.getFragmentManager(); } catch (Throwable e) { failure.set(e); } });
        worker.start(); worker.join();
        check(failure.get() instanceof IllegalStateException, "Wrong-thread lazy manager creation");
        host.performDestroy(); other.performDestroy(); fresh.performDestroy();
    }
    static void exceptionsAndReentrancy() {
        Host host = new Host(); FragmentManager manager = host.getFragmentManager();
        View foreign = new View(host); host.root.addView(foreign, new ViewGroup.LayoutParams(10, 10));
        Page bad = new Page() {
            @Override public View onCreateView(LayoutInflater inflater, ViewGroup container) { return foreign; }
        };
        expect(IllegalStateException.class, () -> manager.beginTransaction().add(100, bad).commitNow());
        check(!bad.isAdded() && manager.getFragments().isEmpty() && foreign.getParent() == host.root, "Reject attached View without stealing it");
        Page broken = new Page() {
            @Override public void onViewCreated(View view) { throw new IllegalArgumentException("callback"); }
        };
        expect(IllegalArgumentException.class, () -> manager.beginTransaction().add(100, broken).commitNow());
        check(broken.getView() == null && !broken.isAdded() && host.root.getChildCount() == 1, "Callback failure cleanup");
        Page next = new Page();
        Page reentrant = new Page() {
            @Override public void onViewCreated(View view) {
                expect(IllegalStateException.class, () -> manager.beginTransaction().add(100, new Page()).commitNow());
                manager.beginTransaction().replace(100, next).commit();
            }
        };
        manager.beginTransaction().add(100, reentrant).commitNow();
        manager.executePendingTransactions();
        check(next.isAdded() && !reentrant.isAdded(), "Callbacks may queue transactions");
        Page throwsOnDestroy = new Page() {
            @Override public void onDestroyView() { throw new IllegalArgumentException("cleanup"); }
        };
        manager.beginTransaction().add(100, throwsOnDestroy).commitNow();
        expect(IllegalArgumentException.class, () -> host.performDestroy());
        check(manager.getFragments().isEmpty() && next.getView() == null && throwsOnDestroy.getView() == null && !throwsOnDestroy.isAdded(), "Destroy continues after failed callback");
    }
    static void inputCleanup() {
        Host host = new Host();
        View child = new View(host); child.setFocusable(true);
        final int[] clicks = {0}; child.setOnClickListener(v -> clicks[0]++);
        host.root.addView(child, new ViewGroup.LayoutParams(100, 100));
        child.layout(0, 0, 100, 100); child.requestFocus();
        host.root.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN, 10, 10));
        check(child.isPressed() && child.isFocused(), "Input setup");
        host.root.removeView(child);
        host.root.dispatchTouchEvent(new MotionEvent(MotionEvent.ACTION_UP, 10, 10));
        check(!child.isPressed() && !child.isFocused() && child.getParent() == null && clicks[0] == 0, "Removal cancels input and clears focus");
        host.root.removeView(child); host.performDestroy();
    }
    static void destructionDuringCallbacks() {
        for (int phase = 0; phase < 4; phase++) {
            final int destroyAt = phase;
            Host host = new Host();
            Page page = new Page() {
                @Override public void onAttach(Context context) {
                    super.onAttach(context);
                    if (destroyAt == 0) host.performDestroy();
                }
                @Override public void onCreate() {
                    super.onCreate();
                    if (destroyAt == 1) host.performDestroy();
                }
                @Override public View onCreateView(LayoutInflater inflater, ViewGroup container) {
                    View view = super.onCreateView(inflater, container);
                    if (destroyAt == 2) host.performDestroy();
                    return view;
                }
                @Override public void onViewCreated(View view) {
                    super.onViewCreated(view);
                    if (destroyAt == 3) host.performDestroy();
                }
            };
            Page laterOperation = new Page();
            FragmentManager manager = host.getFragmentManager();
            manager.beginTransaction().add(100, page).add(100, laterOperation).commitNow();
            check(host.isDestroyed() && manager.getFragments().isEmpty(), "Callback destruction membership: " + phase);
            check(page.getView() == null && page.getActivity() == null && !page.isResumed()
                    && host.root.getChildCount() == 0 && laterOperation.events.isEmpty(), "Callback destruction cleanup: " + phase);
            if (phase < 2) check(!page.events.contains("createView"), "Creation continued after destruction");
        }
        Host host = new Host();
        Page closer = new Page() {
            @Override public void onResume() { super.onResume(); host.performDestroy(); }
        };
        Page follower = new Page();
        host.getFragmentManager().beginTransaction().add(100, closer).add(100, follower).commitNow();
        host.performResume();
        check(!follower.isAdded() && !follower.isResumed() && !follower.events.contains("resume"), "Do not resume destroyed snapshot entries");

        Host duringCreation = new Host();
        Page creator = new Page() {
            @Override public void onCreate() { super.onCreate(); duringCreation.performResume(); }
        };
        duringCreation.getFragmentManager().beginTransaction().add(100, creator).commitNow();
        check(creator.events.equals(Arrays.asList("attach", "create", "createView", "viewCreated", "resume")), "Resume waits for view creation");
        duringCreation.performDestroy();
    }
    static void removalFailures() {
        Host host = new Host();
        RuntimeException focusFailure = new IllegalStateException("focus");
        RuntimeException destroyFailure = new IllegalArgumentException("destroyView");
        Page page = new Page() {
            @Override public View onCreateView(LayoutInflater inflater, ViewGroup container) {
                return new View(host) { @Override public void clearFocus() { throw focusFailure; } };
            }
            @Override public void onDestroyView() { super.onDestroyView(); throw destroyFailure; }
        };
        FragmentManager manager = host.getFragmentManager();
        manager.beginTransaction().add(100, page).commitNow();
        View old = page.requireView();
        try {
            manager.beginTransaction().remove(page).commitNow();
            throw new AssertionError("Expected focus exception");
        } catch (RuntimeException failure) {
            check(failure == focusFailure && Arrays.asList(failure.getSuppressed()).contains(destroyFailure), "Preserve cleanup exceptions");
        }
        check(old.getParent() == null && host.root.getChildCount() == 0 && page.getView() == null
                && !page.isAdded() && page.getActivity() == null && manager.getFragments().isEmpty(), "Removal survives focus exception");
        check(page.events.containsAll(Arrays.asList("destroyView", "destroy", "detach")), "All cleanup callbacks run");
        host.performDestroy();

        Host errors = new Host();
        AssertionError shared = new AssertionError("shared cleanup error");
        Page one = new Page() { @Override public void onDestroy() { throw shared; } };
        Page two = new Page() { @Override public void onDestroy() { throw shared; } };
        Page three = new Page();
        errors.getFragmentManager().beginTransaction().add(100, one).add(100, two).add(100, three).commitNow();
        expect(AssertionError.class, errors::performDestroy);
        check(!one.isAdded() && !two.isAdded() && !three.isAdded() && three.getView() == null, "Errors must not interrupt other fragment cleanup");
    }
    static void boundedSynchronousDrain() {
        Host host = new Host();
        FragmentManager manager = host.getFragmentManager();
        final int[] created = {0};
        class Producer extends Fragment {
            @Override public void onCreate() {
                if (++created[0] < 5) manager.beginTransaction().add(100, new Producer()).commit();
            }
        }
        manager.beginTransaction().add(100, new Producer()).commit();
        manager.executePendingTransactions();
        check(created[0] == 1, "Synchronous drain must defer callback submissions");
        manager.executePendingTransactions();
        check(created[0] == 2, "Deferred producer continues on next drain");
        host.performDestroy();
    }
    static void apk(String path) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[] {new File(path).toURI().toURL()}, FragmentTest.class.getClassLoader())) {
            Host host = new Host(loader);
            Class<?> ids = loader.loadClass("com.packages.fragmentdemo.R$id");
            int content = ids.getField("content").getInt(null);
            int button = ids.getField("switch_page").getInt(null);
            host.root.setId(content);
            Fragment page = (Fragment) loader.loadClass("com.packages.fragmentdemo.MainActivity$PageFragment")
                    .getConstructor(boolean.class).newInstance(false);
            FragmentManager manager = host.getFragmentManager();
            manager.beginTransaction().add(content, page, "first").commitNow(); host.performResume();
            check(page.requireView().findViewById(button).performClick(), "APK layout click");
            check(page.isAdded(), "Click replacement is queued");
            manager.executePendingTransactions();
            Fragment second = manager.findFragmentByTag("second");
            check(second != null && second.isResumed() && !page.isAdded(), "APK replacement");
            second.requireView().findViewById(button).performClick(); manager.executePendingTransactions();
            check(manager.findFragmentByTag("first") != null && !second.isAdded(), "APK switch back");
            host.performDestroy();
        }
    }
    static final class Done extends RuntimeException {}
    public static void main(String[] args) throws Exception {
        Host noLooper = new Host();
        FragmentManager synchronous = noLooper.getFragmentManager();
        Fragment headless = new Fragment();
        synchronous.beginTransaction().add(100, headless).commitNow();
        noLooper.performResume();
        check(headless.isResumed() && headless.getView() == null, "Headless Fragment");
        expect(IllegalStateException.class, () -> synchronous.beginTransaction().add(100, new Page()).commit());
        noLooper.performDestroy();
        check(!headless.isAdded(), "Headless cleanup");
        Looper.prepareMainLooper();
        lifecycle(); transactions(); exceptionsAndReentrancy(); inputCleanup();
        destructionDuringCallbacks(); removalFailures(); boundedSynchronousDrain();
        if (args.length > 0) apk(args[0]);
        // Real Looper execution must yield to unrelated work between bounded batches.
        Host host = new Host();
        final int[] created = {0}, turns = {0};
        for (int i = 0; i < 70; i++) {
            host.getFragmentManager().beginTransaction().add(100, new Fragment() {
                @Override public void onCreate() { created[0]++; }
            }).commit();
        }
        Handler end = new Handler() {
            @Override public void handleMessage(com.android.os.Message message) {
                turns[0]++;
                if (turns[0] == 1) check(created[0] > 0 && created[0] < 70, "Async drain must yield to other UI work");
                if (created[0] < 70) sendMessage(obtainMessage(0, null));
                else { host.performDestroy(); throw new Done(); }
            }
        };
        end.sendMessage(end.obtainMessage(0, null));
        try { Looper.loop(); } catch (Done done) { System.out.println("FragmentTest passed"); }
    }
}
