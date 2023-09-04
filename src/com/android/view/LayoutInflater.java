package com.android.view;

import com.android.content.Context;
import com.android.res.Resources;
import com.android.util.AttributeSet;
import com.android.util.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import com.android.widget.*;

public abstract class LayoutInflater {
    protected final Context mContext;

    static final Class<?>[] mConstructorSignature = new Class[] {
            Context.class, AttributeSet.class};
    final Object[] mConstructorArgs = new Object[2];

    private String TAG="LayoutInflater.";

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

    public View inflate(int resource, ViewGroup root) {
        return inflate(resource, root, root != null);
    }

    public View inflate( int resource,  ViewGroup root, boolean attachToRoot) {
        Log.d(TAG, "inflate ");
        final Resources res = getContext().getResources();

        XmlPullParser parser = null;
        try {
            parser = res.getLayout(resource);
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            return inflate(parser, root, attachToRoot);
        } finally {
//            parser.close();
        }
    }

    public View inflate(XmlPullParser parser,  ViewGroup root, boolean attachToRoot) {
        Log.d(TAG, "inflate");
        final Context inflaterContext = mContext;
//        final AttributeSet attrs = Xml.asAttributeSet(parser);
        final AttributeSet attrs = null;
        mConstructorArgs[0] = inflaterContext;
        View result = root;
        try{
//            快速跳到根节点
            advanceToRootNode( parser);
            final String name = parser.getName();

            final  View temp = createViewFromTag(root, name, inflaterContext, attrs);
            ViewGroup.LayoutParams params = null;
//
            rInflateChildren( parser, temp, attrs, true);

            if( root!=null && attachToRoot){
                root.addView( temp, params );
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return result;
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
        rInflate(parser, parent, parent.getContext(), attrs, finishInflate);
    }

    void rInflate(XmlPullParser parser, View parent, Context context,
                  AttributeSet attrs, boolean finishInflate) throws XmlPullParserException, IOException {
        final int depth = parser.getDepth();
        int type;

        while (((type = parser.next()) != XmlPullParser.END_TAG ||
                parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {

            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            final String name = parser.getName();
            Log.d(TAG,"name "+name);
            final View view = createViewFromTag(parent, name, context, attrs);
            final ViewGroup viewGroup = (ViewGroup) parent;
            final ViewGroup.LayoutParams params = viewGroup.generateLayoutParams(attrs);
            rInflateChildren(parser, view, attrs, true);
            viewGroup.addView(view, params);
        }
    }
    private View createViewFromTag(View parent, String name, Context context, AttributeSet attrs){
        return createViewFromTag( parent, name, context, attrs, false);
    }

    View createViewFromTag(View parent, String name, Context context, AttributeSet attrs,
                           boolean ignoreThemeAttr) {
        try{
            View view = tryCreateView(parent, name, context, attrs);
            if( view == null ){
                view = onCreateView(context, parent, name, attrs);
            }
            return view;
        }catch (Exception e){

        }
        return  null;
    }

    public final View tryCreateView( View parent,  String name,
                                     Context context,
                                     AttributeSet attrs) {
        View view=null;

        return  view;
    }
    protected View onCreateView(View parent, String name, AttributeSet attributeSet){
        return  onCreateView(name, attributeSet);
    }

    protected View onCreateView( String name ,AttributeSet attrs ){
        return  createView(name, "", attrs);
    }
    public View onCreateView( Context viewContext,  View parent, String name,  AttributeSet attrs) {
        return onCreateView(parent, name, attrs);
    }
    public final View createView(String name,String prefix, AttributeSet attrs ){

        return  createView( mContext, name, prefix, attrs);
    }

    public final View createView( Context context, String name, String prefix, AttributeSet attrs){
        Class<? extends View> clazz = null;
        Constructor<? extends View> constructor;
        try {
            Log.d(TAG, "prefix: "+prefix  +" "+ "name: "+name);
            clazz = Class.forName(prefix != null ? (prefix + name) : name).asSubclass(View.class);;
            constructor = clazz.getConstructor(mConstructorSignature);
            constructor.setAccessible(true);

            Object[] args = mConstructorArgs;
            try {
                final View view = constructor.newInstance(args);
                return view;
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return  null;
    }
}
