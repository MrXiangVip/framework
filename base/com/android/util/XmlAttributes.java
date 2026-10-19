package com.android.util;

import java.util.HashMap;
import java.util.Map;
import org.xmlpull.v1.XmlPullParser;

/** Snapshot: parser advancement must not change the attributes of an existing View. */
public final class XmlAttributes implements AttributeSet {
    private final Map<String, String> values = new HashMap<>();
    public XmlAttributes(XmlPullParser parser) {
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            values.put(parser.getAttributeNamespace(i) + ":" + parser.getAttributeName(i),
                    parser.getAttributeValue(i));
        }
    }
    @Override public String getAttributeValue(String namespace, String name) {
        return values.get(namespace + ":" + name);
    }
}
