package com.android.pm;
import java.io.File;
public class PackageParser2 {
    private final ParsingPackageUtils parsingUtils = new ParsingPackageUtils();
    public ParsedPackage parsePackage(File packageFile, int flags, boolean useCaches) {
        // No cache yet: rescans always read current metadata.
        return parsingUtils.parsePackage(packageFile);
    }
}