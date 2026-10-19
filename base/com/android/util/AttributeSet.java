package com.android.util;

public interface AttributeSet {
    default String getAttributeValue(String namespace, String name) { return null; }
    default String get(String name) {
        return getAttributeValue("http://schemas.android.com/apk/res/android", name);
    }
    default int dimension(String name, int fallback) {
        String value = get(name);
        if (value == null) return fallback;
        if ("match_parent".equals(value) || "fill_parent".equals(value)) return -1;
        if ("wrap_content".equals(value)) return -2;
        return Math.max(0, Math.round(Float.parseFloat(value.replaceAll("(dp|dip|sp|px)$", ""))));
    }
    default int color(String name, int fallback) {
        String value = get(name);
        if (value == null) return fallback;
        if (!value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
            throw new IllegalArgumentException("Unsupported color: " + value);
        }
        long color = Long.parseLong(value.substring(1), 16);
        return (int) (value.length() == 7 ? color | 0xff000000L : color);
    }
}
