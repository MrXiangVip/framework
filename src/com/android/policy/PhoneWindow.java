package com.android.policy;

import com.android.view.View;
import com.android.view.ViewGroup;
import com.android.view.Window;
import com.android.content.Context;
import com.android.util.Log;
import com.android.view.LayoutInflater;

import java.io.File;

public class PhoneWindow extends Window {
    private String TAG="PhoneWindow.";
    private DecorView mDecor;
    private LayoutInflater mLayoutInflater;
    ViewGroup mContentParent;

    String DIR="/home/xshx/IdeaProjects/untitled/res/layout";
    String FileName="activity_main.xml";

    public PhoneWindow(Context context) {
        super(context);
        mLayoutInflater = LayoutInflater.from(context);

    }
    public PhoneWindow(Context context, Window preservedWindow){
        this(context);

    }
	
    public void setContentView(File file) {
        Log.d(TAG, "setContentView ");
        if( mContentParent ==null ){
            installDecor();
        }
        mLayoutInflater.inflate(file, mContentParent);

    }
    public final View getDecorView() {
        if (mDecor == null ) {
            installDecor();
        }
        return mDecor;
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

        File file = new File(DIR, FileName );
        mDecor.onResourcesLoaded(mLayoutInflater, file);


        return  decor;
    }
    protected DecorView generateDecor(int featureId) {
        Context context;
        context = getContext();
        return new DecorView(context, featureId, this, null);
    }
}