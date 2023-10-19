package com.android.pm;

import java.io.File;

public class PackageParser2 {
    private ParsingPackageUtils parsingUtils;

    public ParsedPackage parsePackage(File packageFile, int flags, boolean useCaches){
//        ParseResult<ParsingPackage> result = parsingUtils.parsePackage(input, packageFile, flags);
//        ParsedPackage parsed = (ParsedPackage) result.getResult().hideAsParsed();
        ParsedPackage parsed = new ParsedPackage();
        return  parsed;
    }
}
