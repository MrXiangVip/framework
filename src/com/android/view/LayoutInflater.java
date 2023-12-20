package com.android.view;

import com.android.content.Context;
import com.android.res.Resources;
import com.android.util.AttributeSet;
import com.android.util.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import com.android.widget.*;
import org.xmlpull.v1.XmlPullParserFactory;

public abstract class LayoutInflater {
    protected final Context mContext;

    static final Class<?>[] mConstructorSignature = new Class[] {
            Context.class, AttributeSet.class};
    final Object[] mConstructorArgs = new Object[2];

    private String TAG="LayoutInflater.";
    private static final String[] sClassPrefixList = {
            "com.widget.",
            "android.widget.",
            "android.webkit.",
            "android.app."
    };
	
    protected LayoutInflater(Context context){
        mContext = context;
    }
    public Context getContext( ){
        return mContext;
    }

    public static LayoutInflater from(Context context) {
        PhoneLayoutInflater phoneLayoutInflater = new PhoneLayoutInflater( context);
        return  phoneLayoutInflater;
    }

    public View inflate(File file, ViewGroup root) {
        return inflate(file, root, root != null);
    }


    public View inflate(File file, ViewGroup root, boolean attachToRoot) {

        try {
            XmlPullParserFactory parserFactory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = parserFactory.newPullParser();
            parser.setInput(new FileInputStream(file), "UTF-8");
            return inflate(parser, root, attachToRoot);

        } catch (Exception e) {

        }
        return null;
    }


    public View inflate(XmlPullParser parser,  ViewGroup root, boolean attachToRoot) {
        Log.d(TAG, "inflate");
        View result = root;
        try{
//            快速跳到根节点
            advanceToRootNode(parser);
            final String name = parser.getName();

            final View temp = createViewFromTag(root, name, mContext, null);
            rInflateChildren(parser, temp, null, true);
            if( root == null ){
                result = temp;
            }

        }catch (Exception e){
            e.printStackTrace();
        }finally {

        }
        return  result;
    }


    private void advanceToRootNode(XmlPullParser parser) throws IOException, XmlPullParserException {
        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG &&
                type != XmlPullParser.END_DOCUMENT) {
            // Empty
        }

    }

    final void rInflateChildren(XmlPullParser parser, View parent, AttributeSet attrs,
                                boolean finishInflate) throws XmlPullParserException, IOException {
        try {
            rInflate(parser, parent, parent.getContext(), attrs, finishInflate);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    void rInflate(XmlPullParser parser, View parent, Context context,
                  AttributeSet attrs, boolean finishInflate) throws IOException, XmlPullParserException, ClassNotFoundException {
        final int depth = parser.getDepth();
        int type;
        while (((type = parser.next()) != XmlPullParser.END_TAG
                || parser.getDepth() > depth)
                && type != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            final String name = parser.getName();
            Log.d(TAG, " rInflate  name "+name);
            final View view = createViewFromTag(parent, name, context, attrs);
            final ViewGroup viewGroup = (ViewGroup) parent;
            final ViewGroup.LayoutParams params = viewGroup.generateLayoutParams(attrs);
            rInflateChildren(parser, view, attrs, true);
            viewGroup.addView(view, params);
        }
    }
	
    private View createViewFromTag(View parent, String name, Context context, AttributeSet attrs) throws ClassNotFoundException {
        return createViewFromTag(parent, name, context, attrs, false);
    }

    View createViewFromTag(View parent, String name, Context context, AttributeSet attrs,
                           boolean ignoreThemeAttr) throws ClassNotFoundException {
        View view;
        view = onCreateView(context, parent, name, attrs);

        return  view;
    }
	
    public View onCreateView( Context viewContext,  View parent,
                              String name,  AttributeSet attrs)
            throws ClassNotFoundException {
        return onCreateView(parent, name, attrs);
    }
    protected View onCreateView(View parent, String name, AttributeSet attrs)
            throws ClassNotFoundException {
        return onCreateView(name, attrs);
    }
    protected View onCreateView(String name, AttributeSet attrs){
        for (String prefix : sClassPrefixList) {
            try {
                View view = createView(name, prefix, attrs);
                if (view != null) {
                    return view;
                }
            } catch (Exception e) {
                // In this case we want to let the base class take a crack
                // at it.
                return  null;
            }
        }
        return  null;
    }
    public final View createView(String name,String prefix, AttributeSet attrs ){

        return  createView( mContext, name, prefix, attrs);
    }

    public final View createView( Context viewContext,  String name,
                                  String prefix,  AttributeSet attrs){
        Class<? extends View> clazz = null;
        Constructor<? extends View> constructor;
        try{

            clazz = Class.forName(prefix != null ? (prefix + name) : name).asSubclass(View.class);
            constructor = clazz.getConstructor(mConstructorSignature);
            mConstructorArgs[0] = viewContext;
            Object[] args = mConstructorArgs;
            args[1] = attrs;
            View view = constructor.newInstance( args );
            return  view;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return  null;
    }
}
