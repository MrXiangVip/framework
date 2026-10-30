#include "jni.h"
#include "com_android_wm_XWindow.h"
#include <X11/Xlib.h>
#include <X11/Xutil.h>
#include <X11/Xatom.h>
#include <unistd.h>
#include <stdint.h>
#include <stdlib.h>
#include <limits.h>
#include <locale.h>
#include <X11/keysym.h>

typedef struct {
    Display *display;
    Window window;
    GC gc;
    Atom delete_window;
    XIM input_method;
    XIC input_context;
    unsigned char *text;
    int text_capacity, focused, im_registered;
    int text_length, text_offset, text_utf8;
    int width, height, destroyed, closing;
} XSurface;

static const long window_events = ExposureMask | StructureNotifyMask | ButtonPressMask
    | ButtonReleaseMask | PointerMotionMask | FocusChangeMask | KeyPressMask | KeyReleaseMask;

static void input_method_destroyed(XIM method, XPointer client, XPointer data) {
    (void)method; (void)data;
    XSurface *s = (XSurface *)client;
    s->input_context = NULL;
    s->input_method = NULL;
}

static void open_input_method(Display *display, XPointer client, XPointer data) {
    (void)data;
    XSurface *s = (XSurface *)client;
    if (s->closing || s->input_method) return;
    XIM method = XOpenIM(display, NULL, NULL, NULL);
    if (!method) return;
    XIMStyles *styles = NULL;
    XIC context = NULL;
    const XIMStyle wanted[] = { XIMPreeditNothing | XIMStatusNothing, XIMPreeditNone | XIMStatusNone };
    if (!XGetIMValues(method, XNQueryInputStyle, &styles, NULL) && styles) {
        for (unsigned int i = 0; i < sizeof(wanted) / sizeof(wanted[0]) && !context; i++) {
            for (unsigned short j = 0; j < styles->count_styles; j++) {
                if (styles->supported_styles[j] != wanted[i]) continue;
                context = XCreateIC(method, XNInputStyle, wanted[i], XNClientWindow, s->window,
                    XNFocusWindow, s->window, NULL);
                break;
            }
        }
        XFree(styles);
    }
    if (!context) { XCloseIM(method); return; }
    s->input_method = method;
    s->input_context = context;
    XIMCallback destroyed = { (XPointer)s, input_method_destroyed };
    XSetIMValues(method, XNDestroyCallback, &destroyed, NULL);
    long filter_events = 0;
    XGetICValues(context, XNFilterEvents, &filter_events, NULL);
    XSelectInput(display, s->window, window_events | filter_events);
    if (s->focused) XSetICFocus(context);
}

static void fail(JNIEnv *env, const char *message) {
    jclass type = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (type) (*env)->ThrowNew(env, type, message);
}

JNIEXPORT jlong JNICALL Java_com_android_wm_XWindow_nativeOpen
  (JNIEnv *env, jclass type, jint width, jint height, jbyteArray title) {
    (void)type;
    if (width <= 0 || height <= 0) { fail(env, "Invalid X11 window size"); return 0; }
    /* The Java object serializes display access; XInitThreads also enables Xlib thread bookkeeping. */
    if (!XInitThreads()) { fail(env, "XInitThreads failed"); return 0; }
    XSurface *s = calloc(1, sizeof(*s));
    if (!s) { fail(env, "Cannot allocate X11 surface"); return 0; }
    s->display = XOpenDisplay(NULL);
    if (!s->display) { free(s); fail(env, "Cannot open X11 display; check DISPLAY"); return 0; }
    s->text_capacity = 4096;
    s->text = malloc((size_t)s->text_capacity);
    if (!s->text) { XCloseDisplay(s->display); free(s); fail(env, "Cannot allocate input buffer"); return 0; }
    int screen = DefaultScreen(s->display);
    s->width = width; s->height = height;
    s->window = XCreateSimpleWindow(s->display, RootWindow(s->display, screen),
        0, 0, (unsigned)width, (unsigned)height, 0,
        BlackPixel(s->display, screen), WhitePixel(s->display, screen));
    s->gc = XCreateGC(s->display, s->window, 0, NULL);
    s->delete_window = XInternAtom(s->display, "WM_DELETE_WINDOW", False);
    XSetWMProtocols(s->display, s->window, &s->delete_window, 1);
    /* Standard UTF-8 (not JNI modified UTF-8) also preserves supplementary characters. */
    Atom utf8 = XInternAtom(s->display, "UTF8_STRING", False);
    jsize title_length = title ? (*env)->GetArrayLength(env, title) : 0;
    jbyte *title_bytes = title ? (*env)->GetByteArrayElements(env, title, NULL) : NULL;
    if (title && !title_bytes) {
        XFreeGC(s->display, s->gc); XDestroyWindow(s->display, s->window);
        free(s->text); XCloseDisplay(s->display); free(s); return 0;
    }
    const unsigned char *name = title_bytes ? (const unsigned char *)title_bytes
        : (const unsigned char *)"Framework";
    int name_length = title_bytes ? title_length : 9;
    Atom name_properties[] = { XA_WM_NAME, XA_WM_ICON_NAME,
        XInternAtom(s->display, "_NET_WM_NAME", False),
        XInternAtom(s->display, "_NET_WM_ICON_NAME", False) };
    for (unsigned int i = 0; i < sizeof(name_properties) / sizeof(name_properties[0]); i++)
        XChangeProperty(s->display, s->window, name_properties[i], utf8, 8,
            PropModeReplace, name, name_length);
    if (title_bytes) (*env)->ReleaseByteArrayElements(env, title, title_bytes, JNI_ABORT);
    XSelectInput(s->display, s->window, window_events);
    /* Let window managers and integration checks associate the window with its application process. */
    unsigned long pid = (unsigned long)getpid();
    Atom pid_property = XInternAtom(s->display, "_NET_WM_PID", False);
    XChangeProperty(s->display, s->window, pid_property, XA_CARDINAL, 32,
        PropModeReplace, (unsigned char *)&pid, 1);
    setlocale(LC_CTYPE, "");
    XSetLocaleModifiers("");
    s->im_registered = XRegisterIMInstantiateCallback(s->display, NULL, NULL, NULL,
        open_input_method, (XPointer)s);
    open_input_method(s->display, (XPointer)s, NULL);
    XMapWindow(s->display, s->window);
    XFlush(s->display);
    return (jlong)(intptr_t)s;
}

JNIEXPORT jintArray JNICALL Java_com_android_wm_XWindow_nativePoll
  (JNIEnv *env, jclass type, jlong handle) {
    (void)type;
    XSurface *s = (XSurface *)(intptr_t)handle;
    /* Keep every press/release in order; replacing a batch with the latest state loses clicks. */
    jint values[3 * 256];
    int count = 0;
    if (s->closing) {
        values[count++] = 2; values[count++] = s->width; values[count++] = s->height;
    }
    while (!s->closing && count < 3 * 256 && (s->text_offset < s->text_length || XPending(s->display))) {
        if (s->text_offset < s->text_length) {
            unsigned int code = s->text[s->text_offset++];
            if (s->text_utf8 && code >= 0xc0) {
                int extra = code < 0xe0 ? 1 : code < 0xf0 ? 2 : 3;
                code &= extra == 1 ? 0x1f : extra == 2 ? 0x0f : 0x07;
                while (extra-- > 0 && s->text_offset < s->text_length)
                    code = (code << 6) | (s->text[s->text_offset++] & 0x3f);
            }
            if (code >= 32 && code != 127 && code <= 0x10ffff) {
                values[count++] = 7; values[count++] = (jint)code; values[count++] = 0;
            }
            continue;
        }
        XEvent event;
        int kind = 0, x = 0, y = 0;
        XNextEvent(s->display, &event);
        if (event.type == FocusIn) { s->focused = 1; if (s->input_context) XSetICFocus(s->input_context); }
        if (event.type == FocusOut) { s->focused = 0; if (s->input_context) XUnsetICFocus(s->input_context); }
        if (XFilterEvent(&event, None)) continue;
        if (event.type == KeyPress) {
            KeySym symbol = NoSymbol;
            int length;
            s->text_utf8 = s->input_context != NULL;
            if (s->input_context) {
                Status status;
                length = Xutf8LookupString(s->input_context, &event.xkey,
                    (char *)s->text, s->text_capacity, &symbol, &status);
                if (status == XBufferOverflow && length > 0 && length <= 1024 * 1024) {
                    unsigned char *grown = realloc(s->text, (size_t)length + 1);
                    if (grown) {
                        s->text = grown; s->text_capacity = length + 1;
                        length = Xutf8LookupString(s->input_context, &event.xkey,
                            (char *)s->text, s->text_capacity, &symbol, &status);
                    }
                }
                if (status != XLookupChars && status != XLookupBoth) length = 0;
                if (status != XLookupKeySym && status != XLookupBoth) symbol = NoSymbol;
                /* A committed string is text, not an Enter command to send a chat. */
                if (length > 0 && symbol == XK_Return && !(length == 1 && (s->text[0] == '\r' || s->text[0] == '\n'))) symbol = NoSymbol;
            } else length = XLookupString(&event.xkey, (char *)s->text, s->text_capacity, &symbol, NULL);
            s->text_offset = 0;
            s->text_length = event.xkey.state & (ControlMask | Mod1Mask) ? 0 : length;
            if (symbol != NoSymbol) { kind = 8; x = (int)symbol; y = (int)event.xkey.state; }
        }
        if (event.type == KeyRelease) {
            /* Traditional X11 auto-repeat emits release/press pairs; the key remains held. */
            int repeat = 0;
            if (XPending(s->display)) {
                XEvent next;
                XPeekEvent(s->display, &next);
                repeat = next.type == KeyPress && next.xkey.keycode == event.xkey.keycode
                    && next.xkey.time == event.xkey.time;
            }
            if (!repeat) {
                KeySym symbol = XLookupKeysym(&event.xkey, 0);
                if (symbol != NoSymbol) { kind = 9; x = (int)symbol; y = (int)event.xkey.state; }
            }
        }
        if (event.type == Expose) { kind = 1; x = s->width; y = s->height; }
        if (event.type == ConfigureNotify) {
            s->width = event.xconfigure.width; s->height = event.xconfigure.height;
            kind = 1; x = s->width; y = s->height;
        }
        /* Preserve legacy left-click/drag kinds; add wheel, right button and hover. */
        if (event.type == ButtonPress || event.type == ButtonRelease) {
            if (event.xbutton.button == Button1) kind = event.type == ButtonPress ? 3 : 4;
            else if (event.xbutton.button == Button3) kind = event.type == ButtonPress ? 11 : 12;
            else if (event.type == ButtonPress && event.xbutton.button == Button4) kind = 10;
            else if (event.type == ButtonPress && event.xbutton.button == Button5) kind = 14;
            x = event.xbutton.x; y = event.xbutton.y;
        }
        if (event.type == MotionNotify) {
            kind = event.xmotion.state & Button3Mask ? 15 : event.xmotion.state & Button1Mask ? 5 : 13;
            x = event.xmotion.x; y = event.xmotion.y;
        }
        if (event.type == FocusOut) kind = 6;
        if (event.type == ClientMessage && (Atom)event.xclient.data.l[0] == s->delete_window) s->closing = 1;
        if (event.type == DestroyNotify) { s->destroyed = 1; s->closing = 1; }
        if (s->closing) { kind = 2; x = s->width; y = s->height; }
        /* Pack pointer modifiers above the legacy kind byte; keyboard batches keep their existing format. */
        if ((event.type == ButtonPress || event.type == ButtonRelease || event.type == MotionNotify) && kind)
            kind |= ((event.type == MotionNotify ? event.xmotion.state : event.xbutton.state) & 0xff) << 8;
        if (kind) { values[count++] = kind; values[count++] = x; values[count++] = y; }
    }
    jintArray result = (*env)->NewIntArray(env, count);
    if (result && count) (*env)->SetIntArrayRegion(env, result, 0, count, values);
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
    s->closing = 1;
    if (s->im_registered) XUnregisterIMInstantiateCallback(s->display, NULL, NULL, NULL,
        open_input_method, (XPointer)s);
    if (s->input_context) XDestroyIC(s->input_context);
    if (s->input_method) XCloseIM(s->input_method);
    XFreeGC(s->display, s->gc);
    if (!s->destroyed) XDestroyWindow(s->display, s->window);
    XCloseDisplay(s->display);
    free(s->text);
    free(s);
}
