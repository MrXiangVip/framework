package com.wave;

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
}
