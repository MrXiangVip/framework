package com.android.policy;

import com.android.content.Context;
import com.android.view.LayoutInflater;
import com.android.view.View;
import com.android.view.ViewGroup;
import com.android.view.Window;
import java.io.File;
import java.util.Objects;

public class PhoneWindow extends Window {
    private DecorView mDecor;
    private final LayoutInflater mLayoutInflater;
    private ViewGroup mContentParent;

    public PhoneWindow(Context context) {
        super(context);
        mLayoutInflater = LayoutInflater.from(context);

    }
    public PhoneWindow(Context context, Window preservedWindow){
        this(context);

    }

    @Override public void setContentView(String layoutPath) {
        installDecor();
        // Inflate before replacing content: a bad layout must not erase the old View tree.
        setContentView(mLayoutInflater.inflate(layoutPath, mContentParent, false));
    }

    @Override public void setContentView(File file) {
        installDecor();
        setContentView(mLayoutInflater.inflate(file, mContentParent, false));
    }

    @Override public void setContentView(View view) {
        Objects.requireNonNull(view, "content view");
        installDecor();
        mContentParent.removeAllViews();
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        mContentParent.addView(view, params);
    }

    @Override public final View getDecorView() {
        installDecor();
        return mDecor;
    }
    private void installDecor() {
        if (mDecor == null) mDecor = generateDecor(-1);
        if (mContentParent == null) mContentParent = generateLayout(mDecor);
    }

    protected ViewGroup generateLayout(DecorView decor) {
        return decor.createContentRoot();
    }
    protected DecorView generateDecor(int featureId) {
        return new DecorView(getContext(), featureId, this, null);
    }
}