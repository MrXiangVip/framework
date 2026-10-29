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

    @Override
    public String toString() {
        return toString(System.nanoTime() / 1_000_000L);
    }

    String toString(long now) {
        StringBuilder b = new StringBuilder();
        b.append("{ when=");
        b.append(when - now).append("ms");

        if (target != null) {
            if (callback != null) {
                b.append(" callback=");
                b.append(callback.getClass().getName());
            } else {
                b.append(" what=");
                b.append(what);
            }

            if (arg1 != 0) {
                b.append(" arg1=");
                b.append(arg1);
            }

            if (arg2 != 0) {
                b.append(" arg2=");
                b.append(arg2);
            }

            if (obj != null) {
                b.append(" obj=");
                b.append(obj);
            }

            b.append(" target=");
            b.append(target.getClass().getName());
        } else {
            b.append(" barrier=");
            b.append(arg1);
        }

        b.append(" }");
        return b.toString();
    }
}
