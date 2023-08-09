package com.wave;

import java.io.File;

public class PhoneWindow extends  Window {
    private DecorView mDecor;
    private LayoutInflater mLayoutInflater;
    ViewGroup mContentParent;
    private String TAG="PhoneWindow.";

    public PhoneWindow(Context context) {
        super(context);
        mLayoutInflater = LayoutInflater.from(context);

    }
    public PhoneWindow(Context context, Window preservedWindow){
        this(context);

    }
    public void setContentView(int layoutResID) {
        Log.d(TAG, "setContentView "+layoutResID);
        if( mContentParent ==null ){
            installDecor();
        }

        mLayoutInflater.inflate(layoutResID, mContentParent);

    }

    private void installDecor() {
        if (mDecor == null) {
            mDecor = generateDecor(-1);

        }
        if( mContentParent == null ){
            mContentParent = generateLayout(mDecor);
        }
    }
    protected ViewGroup generateLayout(DecorView decor) {
        ViewGroup contentParent =null;
        return  contentParent;
    }
    protected DecorView generateDecor(int featureId) {
        Context context;
        context = getContext();
        return new DecorView(context, featureId, this, null);
    }
}