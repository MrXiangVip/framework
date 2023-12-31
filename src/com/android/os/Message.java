/*
*源码路径: frameworks/base/core/java/android/os/Message.java
* */
package com.android.os;

public final class Message {
    public int what;
    public int arg1;
    public int arg2;
    public Object obj;
    public long when;

    /*package*/ Runnable callback;
    /*package*/ Message next;

    /*package*/ Handler target;
    public Message() {

    }
    public static Message obtain() {
        return new Message();
    }

    public static Message obtain(Handler h, int what, Object obj) {
        Message m = obtain();
        m.target = h;
        m.what = what;
        m.obj = obj;

        return m;
    }
}
