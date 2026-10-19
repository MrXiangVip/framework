#include "jni.h"
#include "com_android_wm_XWindow.h"
#include <X11/Xlib.h>
#include <X11/Xutil.h>
#include <stdint.h>
#include <stdlib.h>
#include <limits.h>

typedef struct {
    Display *display;
    Window window;
    GC gc;
    Atom delete_window;
    int width, height, destroyed, closing;
} XSurface;

static void fail(JNIEnv *env, const char *message) {
    jclass type = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (type) (*env)->ThrowNew(env, type, message);
}

JNIEXPORT jlong JNICALL Java_com_android_wm_XWindow_nativeOpen
  (JNIEnv *env, jclass type, jint width, jint height) {
    (void)type;
    if (width <= 0 || height <= 0) { fail(env, "Invalid X11 window size"); return 0; }
    /* The Java object serializes display access; XInitThreads also enables Xlib thread bookkeeping. */
    if (!XInitThreads()) { fail(env, "XInitThreads failed"); return 0; }
    XSurface *s = calloc(1, sizeof(*s));
    if (!s) { fail(env, "Cannot allocate X11 surface"); return 0; }
    s->display = XOpenDisplay(NULL);
    if (!s->display) { free(s); fail(env, "Cannot open X11 display; check DISPLAY"); return 0; }
    int screen = DefaultScreen(s->display);
    s->width = width; s->height = height;
    s->window = XCreateSimpleWindow(s->display, RootWindow(s->display, screen),
        0, 0, (unsigned)width, (unsigned)height, 0,
        BlackPixel(s->display, screen), WhitePixel(s->display, screen));
    s->gc = XCreateGC(s->display, s->window, 0, NULL);
    s->delete_window = XInternAtom(s->display, "WM_DELETE_WINDOW", False);
    XSetWMProtocols(s->display, s->window, &s->delete_window, 1);
    XStoreName(s->display, s->window, "Launcher - framework View tree");
    XSelectInput(s->display, s->window, ExposureMask | StructureNotifyMask);
    XMapWindow(s->display, s->window);
    XFlush(s->display);
    return (jlong)(intptr_t)s;
}

JNIEXPORT jintArray JNICALL Java_com_android_wm_XWindow_nativePoll
  (JNIEnv *env, jclass type, jlong handle) {
    (void)type;
    XSurface *s = (XSurface *)(intptr_t)handle;
    jint values[3] = {0, s->width, s->height};
    while (XPending(s->display)) {
        XEvent event;
        XNextEvent(s->display, &event);
        if (event.type == Expose) values[0] |= 1;
        if (event.type == ConfigureNotify) {
            s->width = event.xconfigure.width;
            s->height = event.xconfigure.height;
            values[0] |= 1;
        }
        if (event.type == ClientMessage && (Atom)event.xclient.data.l[0] == s->delete_window) s->closing = 1;
        if (event.type == DestroyNotify) { s->destroyed = 1; s->closing = 1; }
    }
    if (s->closing) values[0] |= 2;
    values[1] = s->width; values[2] = s->height;
    jintArray result = (*env)->NewIntArray(env, 3);
    if (result) (*env)->SetIntArrayRegion(env, result, 0, 3, values);
    return result;
}

/* Convert Java RGB to the server visual's pixel masks instead of assuming byte order/depth. */
static unsigned long component(unsigned int value, unsigned long mask) {
    if (!mask) return 0;
    unsigned shift = 0;
    while (!(mask & 1)) { mask >>= 1; shift++; }
    return (((unsigned long)value * mask / 255) << shift);
}

JNIEXPORT void JNICALL Java_com_android_wm_XWindow_nativePresent
  (JNIEnv *env, jclass type, jlong handle, jintArray pixels, jint width, jint height) {
    (void)type;
    XSurface *s = (XSurface *)(intptr_t)handle;
    if (s->closing || s->destroyed) return;
    if (width <= 0 || height <= 0 || width > INT_MAX / height
            || (*env)->GetArrayLength(env, pixels) < width * height) {
        fail(env, "Invalid X11 frame buffer"); return;
    }
    int screen = DefaultScreen(s->display);
    XImage *image = XCreateImage(s->display, DefaultVisual(s->display, screen),
        (unsigned)DefaultDepth(s->display, screen), ZPixmap, 0, NULL,
        (unsigned)width, (unsigned)height, 32, 0);
    if (!image) { fail(env, "XCreateImage failed"); return; }
    image->data = calloc((size_t)image->bytes_per_line, (size_t)height);
    if (!image->data) { XDestroyImage(image); fail(env, "Cannot allocate X11 image"); return; }
    jint *data = (*env)->GetIntArrayElements(env, pixels, NULL);
    if (!data) { XDestroyImage(image); return; }
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            unsigned int rgb = (unsigned int)data[y * width + x];
            unsigned long pixel = component((rgb >> 16) & 255, image->red_mask)
                | component((rgb >> 8) & 255, image->green_mask)
                | component(rgb & 255, image->blue_mask);
            XPutPixel(image, x, y, pixel);
        }
    }
    (*env)->ReleaseIntArrayElements(env, pixels, data, JNI_ABORT);
    XPutImage(s->display, s->window, s->gc, image, 0, 0, 0, 0, (unsigned)width, (unsigned)height);
    XFlush(s->display);
    XDestroyImage(image);
}

JNIEXPORT void JNICALL Java_com_android_wm_XWindow_nativeClose
  (JNIEnv *env, jclass type, jlong handle) {
    (void)env; (void)type;
    XSurface *s = (XSurface *)(intptr_t)handle;
    XFreeGC(s->display, s->gc);
    if (!s->destroyed) XDestroyWindow(s->display, s->window);
    XCloseDisplay(s->display);
    free(s);
}
