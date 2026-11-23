package com.android.res;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.Base64;
import java.util.Locale;
import java.nio.charset.StandardCharsets;

/** Resources belonging to a single application's class loader. */
public class Resources {
    private static final String INDEX = "META-INF/framework/resources.properties";
    private final ClassLoader loader;
    private final Map<Integer, String> layouts = new HashMap<>();
    private final Map<Integer, String> drawables = new HashMap<>();
    private final Map<String, Integer> drawableIds = new HashMap<>();
    private final Map<String, Integer> ids = new HashMap<>();
    private final Map<Integer, String> idNames = new HashMap<>();
    private final Map<Integer, String> strings = new HashMap<>();
    private final Map<String, Integer> stringIds = new HashMap<>();

    public static class NotFoundException extends IllegalArgumentException {
        public NotFoundException(String message) { super(message); }
    }

    public Resources() { this(Resources.class.getClassLoader()); }

    public Resources(ClassLoader loader) {
        if (loader == null) throw new NullPointerException("loader");
        this.loader = loader;
        try (InputStream in = loader.getResourceAsStream(INDEX)) {
            // System context and older APKs may have no generated resource table.
            if (in == null) return;
            Properties entries = new Properties();
            entries.load(in);
            for (String key : entries.stringPropertyNames()) {
                if (key.startsWith("id.")) {
                    String name = key.substring(3);
                    int value = Integer.decode(entries.getProperty(key));
                    if (!name.matches("[A-Za-z_$][A-Za-z0-9_$]*") || (value >>> 16) != 0x7f02
                            || idNames.put(value, name) != null) {
                        throw new IllegalArgumentException("Invalid id index entry: " + key);
                    }
                    ids.put(name, value);
                    continue;
                }
                int id = Integer.decode(key);
                String path = entries.getProperty(key);
                if ((id >>> 16) == 0x7f04) {
                    int separator = path.indexOf('|');
                    if (separator < 1 || !path.substring(0, separator).matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
                        throw new IllegalArgumentException("Invalid string index entry: " + key);
                    }
                    String value = new String(Base64.getDecoder().decode(path.substring(separator + 1)), StandardCharsets.UTF_8);
                    if (strings.put(id, value) != null || stringIds.put(path.substring(0, separator), id) != null) {
                        throw new IllegalArgumentException("Duplicate string index entry: " + key);
                    }
                    continue;
                }
                if ((id >>> 16) == 0x7f03) {
                    if (!path.matches("res/drawable/[A-Za-z_$][A-Za-z0-9_$]*\\.(?i:png|jpg|jpeg|gif|bmp)")) {
                        throw new IllegalArgumentException("Invalid drawable index entry: " + key);
                    }
                    String name = path.substring("res/drawable/".length(), path.lastIndexOf('.'));
                    if (drawables.put(id, path) != null || drawableIds.put(name, id) != null) {
                        throw new IllegalArgumentException("Duplicate drawable index entry: " + key);
                    }
                    continue;
                }
                if ((id >>> 16) != 0x7f01 || !path.matches("res/layout/[A-Za-z_$][A-Za-z0-9_$]*\\.xml")
                        || layouts.put(id, path) != null) {
                    throw new IllegalArgumentException("Invalid layout index entry: " + key);
                }
            }
        } catch (IOException | NumberFormatException e) {
            throw new IllegalArgumentException("Cannot load resource index", e);
        }
    }

    /** Returns -1 when the name is absent, including APKs built without an index. */
    public int getId(String name) {
        Integer id = ids.get(name);
        return id == null ? -1 : id;
    }

    public String getIdName(int id) { return idNames.get(id); }

    /** Returns 0 when the string name is absent. */
    public int getStringId(String name) {
        Integer id = stringIds.get(name);
        return id == null ? 0 : id;
    }

    public String getString(int id) {
        String value = strings.get(id);
        if (value == null) throw new NotFoundException("Unknown string resource: 0x" + Integer.toHexString(id));
        return value;
    }

    public String getString(int id, Object... args) {
        return String.format(Locale.getDefault(), getString(id), args);
    }

    /** Returns 0 when no indexed bitmap drawable has this name. */
    public int getDrawableId(String name) {
        Integer id = drawableIds.get(name);
        return id == null ? 0 : id;
    }

    public String getDrawablePath(int id) {
        String path = drawables.get(id);
        if (path == null) throw new NotFoundException("Unknown drawable resource: 0x" + Integer.toHexString(id));
        return path;
    }

    /** This framework represents bitmap drawables as BufferedImage, not android.graphics.drawable.Drawable. */
    public BufferedImage getDrawable(int id) {
        String path = getDrawablePath(id);
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) throw new FileNotFoundException(path);
            BufferedImage bitmap = ImageIO.read(in);
            if (bitmap == null) throw new IOException("Unsupported image: " + path);
            return bitmap;
        } catch (IOException e) {
            NotFoundException error = new NotFoundException("Cannot load drawable: " + path);
            error.initCause(e);
            throw error;
        }
    }

    public String getLayoutPath(int id) {
        String path = layouts.get(id);
        if (path == null) throw new NotFoundException("Unknown layout resource: 0x" + Integer.toHexString(id));
        return path;
    }

    public XmlPullParser getLayout(int id) throws XmlPullParserException, FileNotFoundException {
        String path = getLayoutPath(id);
        // A Reader keeps the parser independent of the stream's lifetime.
        StringWriter xml = new StringWriter();
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) throw new FileNotFoundException(path);
            try (Reader reader = new InputStreamReader(in, "UTF-8")) {
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) xml.write(buffer, 0, count);
            }
        } catch (FileNotFoundException e) { throw e; }
        catch (IOException e) { throw new XmlPullParserException("Cannot read " + path, null, e); }
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader(xml.toString()));
        return parser;
    }
}
