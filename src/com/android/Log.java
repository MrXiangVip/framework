package com.android;

import java.text.SimpleDateFormat;

public final class Log {
    static SimpleDateFormat SDF= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    /**
     * Priority constant for the println method; use Log.v.
     */
    public static final int VERBOSE = 2;

    /**
     * Priority constant for the println method; use Log.d.
     */
    public static final int DEBUG = 3;

    /**
     * Priority constant for the println method; use Log.i.
     */
    public static final int INFO = 4;

    /**
     * Priority constant for the println method; use Log.w.
     */
    public static final int WARN = 5;

    /**
     * Priority constant for the println method; use Log.e.
     */
    public static final int ERROR = 6;

    /**
     * Priority constant for the println method.
     */
    public static final int ASSERT = 7;

    public static int v( String tag,  String msg) {
        System.out.println(tag +" "+msg);
        return  0;
    }

    public static int d( String tag,  String msg) {
        System.out.println(SDF.format(System.currentTimeMillis())+" "+ tag + msg);
        return 0;
    }

    public static int i( String tag,  String msg) {
        System.out.println( tag + msg);
        return 0;
    }

    public static int w( String tag,  String msg) {
        System.out.println( tag + msg);
        return 0;
    }

    public static int e( String tag,  String msg) {
        System.out.println( tag + msg);
        return 0;
    }
    public static int a( String tag,  String msg) {
        System.out.println( tag + msg);
        return 0;
    }
}