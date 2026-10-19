package com.android.pm;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

/** Text-manifest subset of Android's ParsingPackageUtils. */
public class ParsingPackageUtils {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;

    public ParsedPackage parsePackage(File packageFile) {
        Objects.requireNonNull(packageFile, "packageFile");
        try {
            File codePath = packageFile.getCanonicalFile();
            if (codePath.isDirectory()) {
                File manifest = new File(codePath, "AndroidManifest.xml");
                if (manifest.isFile()) {
                    try (InputStream input = new FileInputStream(manifest)) {
                        return parseManifest(input, codePath, null);
                    }
                }
                File[] apks = codePath.listFiles(f -> f.isFile()
                        && PackageManagerService.isApkFile(f));
                if (apks == null || apks.length != 1) {
                    throw new IOException("Expected AndroidManifest.xml or a single APK; splits unsupported");
                }
                // Accept base.apk or named standalone artifacts such as launcher.apk.
                return parseArchive(apks[0], codePath);
            }
            if (!codePath.isFile() || !PackageManagerService.isApkFile(codePath)) {
                throw new IOException("Not an APK or package directory");
            }
            return parseArchive(codePath, codePath);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot parse " + packageFile + ": "
                    + e.getMessage(), e);
        }
    }
    private ParsedPackage parseArchive(File apk, File codePath) throws Exception {
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry entry = zip.getEntry("AndroidManifest.xml");
            if (entry == null) throw new IOException("Missing AndroidManifest.xml");
            try (InputStream input = zip.getInputStream(entry)) {
                return parseManifest(input, codePath, apk.getCanonicalFile());
            }
        }
    }
    private ParsedPackage parseManifest(InputStream input, File codePath, File baseApkPath) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > MAX_MANIFEST_BYTES) throw new IOException("Manifest exceeds 1 MiB");
            bytes.write(buffer, 0, count);
        }
        byte[] manifestBytes = bytes.toByteArray();
        if (manifestBytes.length >= 2 && manifestBytes[0] == 3 && manifestBytes[1] == 0) {
            throw new IOException("Binary AXML unsupported; supply a text AndroidManifest.xml");
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override public void error(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
            @Override public void fatalError(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
        });
        Element manifest = builder.parse(new ByteArrayInputStream(manifestBytes)).getDocumentElement();
        if (!"manifest".equals(manifest.getTagName())) throw new IOException("Expected manifest root");
        String name = manifest.getAttribute("package");
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")) {
            throw new IOException("Invalid package name: " + name);
        }
        String version = attr(manifest, "versionCode");
        long versionCode = version.isEmpty() ? 0 : Long.parseLong(version);
        if (versionCode < 0) throw new IOException("Negative versionCode");
        boolean core = bool(manifest.getAttribute("coreApp"), false);
        List<Element> apps = children(manifest, "application");
        if (apps.size() != 1) throw new IOException("Expected exactly one application");
        Element app = apps.get(0);
        boolean appEnabled = bool(attr(app, "enabled"), true);
        List<ParsedPackage.ActivityInfo> activities = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Element activity : children(app, "activity")) {
            String rawName = attr(activity, "name");
            String activityName = rawName;
            if (activityName.startsWith(".")) activityName = name + activityName;
            else if (activityName.indexOf('.') < 0) activityName = name + "." + activityName;
            if (rawName.isEmpty()
                    || !activityName.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")
                    || !names.add(activityName)) {
                throw new IOException("Invalid or duplicate activity: " + activityName);
            }
            List<ParsedPackage.IntentInfo> filters = new ArrayList<>();
            List<Element> declaredFilters = children(activity, "intent-filter");
            for (Element filter : declaredFilters) {
                // URI matching is not implemented; do not match data filters as data-free intents.
                if (!children(filter, "data").isEmpty()) continue;
                filters.add(new ParsedPackage.IntentInfo(namedChildren(filter, "action"),
                        namedChildren(filter, "category")));
            }
            boolean enabled = appEnabled && bool(attr(activity, "enabled"), true);
            boolean exported = bool(attr(activity, "exported"), !declaredFilters.isEmpty());
            String label = attr(activity, "label");
            if (label.isEmpty()) label = attr(app, "label");
            // This framework packages text XML and literal labels, without an Android resource table.
            if (label.isEmpty() || label.startsWith("@")) label = name.substring(name.lastIndexOf('.') + 1);
            String icon = attr(activity, "icon");
            if (icon.isEmpty()) icon = attr(app, "icon");
            if (label.length() > 512 || icon.length() > 1024) throw new IOException("Label or icon path too long");
            activities.add(new ParsedPackage.ActivityInfo(activityName, enabled, exported, filters, label, icon));
        }
        return new ParsedPackage(name, versionCode, attr(manifest, "versionName"), codePath,
                baseApkPath, core, activities);
    }
    private static String attr(Element element, String name) {
        return element.getAttributeNS(ANDROID, name);
    }
    private static boolean bool(String value, boolean fallback) throws IOException {
        if (value.isEmpty()) return fallback;
        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;
        throw new IOException("Expected literal boolean, got " + value);
    }
    private static List<Element> children(Element element, String name) {
        List<Element> result = new ArrayList<>();
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element && name.equals(node.getNodeName())) result.add((Element) node);
        }
        return result;
    }
    private static Set<String> namedChildren(Element element, String tag) throws IOException {
        Set<String> result = new LinkedHashSet<>();
        for (Element child : children(element, tag)) {
            String name = attr(child, "name");
            if (name.isEmpty()) throw new IOException("Missing name on " + tag);
            result.add(name);
        }
        return result;
    }
}