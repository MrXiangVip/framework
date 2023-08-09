package com.wave;

public class Activity extends  ContextThemeWrapper {
    private static Activity activity;
    private Window mWindow;
    private String TAG="Activity.";

/*    Activity() {
        mWindow = new PhoneWindow(this);
    }*/

//    public static void main(String[] argv) {

//        setContentView( );
//        activity = new Activity();
//        activity.setContentView(1);
//    }

    public void setContentView(int layoutResID)  {
        getWindow().setContentView(layoutResID);
    }

    public Window getWindow() {
        return mWindow;
    }

    public void attach(Context context, ActivityThread aThread, Configuration config, Window window) {
        attachBaseContext(context);

        mWindow = new PhoneWindow(this, window);

    }
    public final void performCreate( ) {
        onCreate( );
    }
    protected void onCreate( ) {
        Log.d(TAG,"onCreate");
    }
}