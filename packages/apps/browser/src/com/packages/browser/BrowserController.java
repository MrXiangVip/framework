package com.packages.browser;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Locale;

/** Navigation policy is separate from UI and Chromium transport. */
public final class BrowserController {
    public static String address(String input) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) return "about:blank";
        if (text.length() > 8192) throw new IllegalArgumentException("Address is too long");
        if (text.equals("about:blank")) return text;
        // Explicit schemes are restricted; ordinary words become a web search.
        if (text.matches("(?i)^[a-z][a-z0-9+.-]*:.*") && !text.matches("^[^/: ]+:\\d+(/.*)?$")) {
            String scheme = text.substring(0, text.indexOf(':')).toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https"))
                throw new IllegalArgumentException("Use an HTTP or HTTPS address");
            return valid(text);
        }
        if (!text.matches(".*\\s.*") && (text.contains(".") || text.startsWith("localhost") || text.startsWith("[")))
            return valid((text.startsWith("localhost") || text.startsWith("127.") || text.startsWith("[::1]")) ? "http://" + text : "https://" + text);
        try { return "https://www.google.com/search?q=" + URLEncoder.encode(text, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
    private static String valid(String text) {
        try {
            URI uri = new URI(text);
            if (uri.getRawAuthority() == null || uri.getRawAuthority().isEmpty() || uri.getRawAuthority().contains("@"))
                throw new IllegalArgumentException("Invalid website address");
            return uri.toASCIIString();
        } catch (URISyntaxException e) { throw new IllegalArgumentException("Invalid website address"); }
    }
    private BrowserController() {}
}
