/* Real native input into the framework window; PID avoids touching unrelated windows. */
#include <X11/Xlib.h>
#include <X11/Xatom.h>
#include <stdlib.h>
#include <string.h>
static Window find_window(Display *d, Window w, unsigned long pid, int depth) {
    Atom type; int format; unsigned long count, remaining; unsigned char *data = NULL;
    XGetWindowProperty(d, w, XInternAtom(d, "_NET_WM_PID", False), 0, 1, False,
            XA_CARDINAL, &type, &format, &count, &remaining, &data);
    if (data) { int match = count == 1 && format == 32 && *(unsigned long *) data == pid; XFree(data); if (match) return w; }
    if (depth <= 0) return None;
    Window root, parent, *children = NULL, result = None; unsigned int size;
    if (XQueryTree(d, w, &root, &parent, &children, &size)) {
        for (unsigned int i = 0; i < size && !result; i++) result = find_window(d, children[i], pid, depth - 1);
        if (children) XFree(children);
    }
    return result;
}
int main(int argc, char **argv) {
    if (argc != 6) return 2;
    Display *d = XOpenDisplay(NULL); if (!d) return 3;
    Window w = find_window(d, DefaultRootWindow(d), strtoul(argv[1], NULL, 10), 8);
    if (!w) { XCloseDisplay(d); return 4; }
    int button = atoi(argv[2]), x = atoi(argv[3]), y = atoi(argv[4]), modifiers = atoi(argv[5]);
    for (int type = ButtonPress; type <= ButtonRelease; type++) {
        XEvent event; memset(&event, 0, sizeof(event));
        event.xbutton.type = type; event.xbutton.display = d; event.xbutton.window = w;
        event.xbutton.root = DefaultRootWindow(d); event.xbutton.same_screen = True;
        event.xbutton.button = button; event.xbutton.x = x; event.xbutton.y = y; event.xbutton.state = modifiers;
        XSendEvent(d, w, False, type == ButtonPress ? ButtonPressMask : ButtonReleaseMask, &event);
    }
    XSync(d, False); XCloseDisplay(d); return 0;
}
