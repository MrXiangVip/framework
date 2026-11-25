package com.android.app;

import com.android.content.Context;
import com.android.view.LayoutInflater;
import com.android.view.View;
import com.android.view.ViewGroup;

/** A reusable UI controller hosted by one Activity. Callbacks run on its UI thread. */
public class Fragment {
    FragmentManager manager;
    Activity activity;
    View view;
    ViewGroup container;
    int containerId;
    String tag;
    enum State { DETACHED, ATTACHED, CREATED, VIEW_CREATED, RESUMED }
    State state = State.DETACHED;
    boolean added, creating;

    public final Activity getActivity() { return activity; }
    public final Context getContext() { return activity; }
    public final Activity requireActivity() {
        if (activity == null) throw new IllegalStateException("Fragment is not attached");
        return activity;
    }
    public final Context requireContext() { return requireActivity(); }
    public final FragmentManager getFragmentManager() { return manager; }
    public final View getView() { return view; }
    public final View requireView() {
        if (view == null) throw new IllegalStateException("Fragment has no View");
        return view;
    }
    public final int getId() { return containerId; }
    public final String getTag() { return tag; }
    public final boolean isAdded() { return added; }
    public final boolean isResumed() { return state == State.RESUMED; }

    public void onAttach(Context context) {}
    public void onCreate() {}
    public View onCreateView(LayoutInflater inflater, ViewGroup container) { return null; }
    public void onViewCreated(View view) {}
    public void onResume() {}
    public void onPause() {}
    public void onDestroyView() {}
    public void onDestroy() {}
    public void onDetach() {}
}
