/*
 frameworks/base/core/java/android/content/res/XmlResourceParser.java
 */

package com.android.res;
import org.xmlpull.v1.XmlPullParser;


public interface XmlResourceParser extends XmlPullParser {

    String getAttributeNamespace (int index);

    public void close();

}