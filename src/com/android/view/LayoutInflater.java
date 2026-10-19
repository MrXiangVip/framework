package com.android.view;

import com.android.content.Context;
import com.android.util.AttributeSet;
import com.android.util.XmlAttributes;
import org.xmlpull.v1.*;
import java.io.*;
import java.util.Objects;

/** Inflates the plain XML resources packaged by this project's APK builder. */
public abstract class LayoutInflater {
    protected final Context mContext;
    protected LayoutInflater(Context context) { mContext = context; }
    public Context getContext() { return mContext; }
    public static LayoutInflater from(Context context) { return new PhoneLayoutInflater(context); }
    public View inflate(String path, ViewGroup root) { return inflate(path, root, root != null); }
    public View inflate(String path, ViewGroup root, boolean attach) {
        Objects.requireNonNull(path, "layoutPath");
        try (InputStream in = mContext.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalArgumentException("Missing layout: " + path);
            return inflateInput(in, root, attach);
        } catch (IOException e) { throw new IllegalArgumentException("Cannot read layout: " + path, e); }
    }
    public View inflate(File file, ViewGroup root) { return inflate(file, root, root != null); }
    public View inflate(File file, ViewGroup root, boolean attach) {
        try (InputStream in = new FileInputStream(file)) { return inflateInput(in, root, attach); }
        catch (IOException e) { throw new IllegalArgumentException("Cannot read layout: " + file, e); }
    }
    private View inflateInput(InputStream in, ViewGroup root, boolean attach) {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(in, "UTF-8");
            return inflate(parser, root, attach);
        } catch (XmlPullParserException e) { throw new IllegalArgumentException("Cannot parse layout", e); }
    }
    public View inflate(XmlPullParser parser, ViewGroup root, boolean attach) {
        try {
            int type = parser.getEventType();
            while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) type = parser.next();
            if (type != XmlPullParser.START_TAG) throw new IllegalArgumentException("Layout has no root");
            AttributeSet attrs = new XmlAttributes(parser);
            View view = inflateElement(parser);
            if (root != null) view.setLayoutParams(root.generateLayoutParams(attrs));
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG) throw new IllegalArgumentException("Multiple layout roots");
            }
            if (root != null && attach) { root.addView(view, view.getLayoutParams()); return root; }
            return view;
        } catch (IOException | XmlPullParserException e) { throw new IllegalArgumentException("Invalid layout", e); }
    }
    private View inflateElement(XmlPullParser parser) throws IOException, XmlPullParserException {
        String name = parser.getName();
        AttributeSet attrs = new XmlAttributes(parser);
        View view = name.indexOf('.') >= 0 ? createView(name, null, attrs) : onCreateView(name, attrs);
        if (view == null) throw new IllegalArgumentException("Unknown View: " + name);
        int depth = parser.getDepth();
        for (;;) {
            int type = parser.next();
            if (type == XmlPullParser.END_DOCUMENT) throw new IllegalArgumentException("Unclosed View: " + name);
            if (type == XmlPullParser.END_TAG && parser.getDepth() == depth) return view;
            if (type == XmlPullParser.START_TAG) {
                if (!(view instanceof ViewGroup)) throw new IllegalArgumentException(name + " cannot contain children");
                ViewGroup parent = (ViewGroup) view;
                AttributeSet childAttrs = new XmlAttributes(parser);
                View child = inflateElement(parser);
                parent.addView(child, parent.generateLayoutParams(childAttrs));
            }
        }
    }
    protected abstract View onCreateView(String name, AttributeSet attrs);
    public final View createView(String name, String prefix, AttributeSet attrs) {
        return createView(mContext, name, prefix, attrs);
    }
    public final View createView(Context context, String name, String prefix, AttributeSet attrs) {
        try {
            Class<? extends View> type = Class.forName(prefix == null ? name : prefix + name,
                    true, context.getClassLoader()).asSubclass(View.class);
            return type.getConstructor(Context.class, AttributeSet.class).newInstance(context, attrs);
        } catch (ClassNotFoundException e) { return null; }
        catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalArgumentException("Cannot create View: " + name, e);
        }
    }
}
