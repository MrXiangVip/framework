//
// Created by xshx on 2021/5/25.
//
// 相当于 Android_9.0.0_2.0.0-ga/frameworks/base/core/jni/com_android_internal_os_Zygote.cpp
#include "jni.h"
#include "com_android_wm_XWindow.h"
#include <X11/Xlib.h>
#include <X11/Xutil.h>
#include <stdio.h>
#include <stdlib.h>

int createXWindow( ){
        Display *display;
        int     screen;
        Window  win;
        XEvent  event;

        display = XOpenDisplay(NULL);
        if(display == NULL)
        {
            printf("Cannot open display\n");
            exit(1);
        }
        screen = XDefaultScreen(display);
        win = XCreateSimpleWindow(display,
                                RootWindow(display, screen),
                                10,
                                10,
                                200,
                                200,
                                1,
                                BlackPixel(display, screen),
                                WhitePixel(display, screen));

        XWMHints hints;
        hints.input = True;
        hints.flags = InputHint;
        XSetWMHints(display, win, &hints);

        XSizeHints *size_hints = XAllocSizeHints();
        size_hints->flags = PMinSize | PMaxSize | PSize;
        size_hints->min_width = 600;
        size_hints->max_width = 600;
        size_hints->min_height = 800;
        size_hints->max_height = 800;
        XSetNormalHints(display, win, size_hints);
        XSetWMSizeHints(display,win , size_hints, PSize | PMinSize | PMaxSize);

        XMapWindow(display, win);
        XSelectInput(display, win, ExposureMask | KeyPressMask);
        while(1)
        {
            XNextEvent(display, &event);
            /* draw or redraw the window */
            if(event.type == Expose)
            {
                XDrawRectangle(display, win, DefaultGC(display, screen), 10, 10, 100, 100);
            }
            /* exit on key press */
            if(event.type == KeyPress)
                break;
        }
        XCloseDisplay(display);
        return 0;
}

// 创建系统进程
JNIEXPORT jint JNICALL Java_com_android_wm_XWindow_nativeCreateAWindow (JNIEnv *env, jobject obj){
    printf("Java_com_android_wm_XWindow_nativeCreateAWindow \n");
    return  createXWindow( );

}






