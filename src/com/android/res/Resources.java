package com.android.res;

import com.android.util.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;

public class Resources {

    private String TAG="Resources.";

    public XmlPullParser getLayout(int id) throws XmlPullParserException, FileNotFoundException {
        Log.d(TAG, "getLayout");
        return loadXmlResourceParser(id, "layout");
    }

    XmlPullParser loadXmlResourceParser( int id,  String type) throws XmlPullParserException, FileNotFoundException {


        XmlBlock block = new XmlBlock();
        return  block.newParser(id);
    }

}