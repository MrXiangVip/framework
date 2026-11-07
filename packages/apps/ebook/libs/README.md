# PDF rendering dependency

`pdfbox-app-3.0.7.jar` is the Apache PDFBox standalone distribution, including its dependencies.
Source: https://repo.maven.apache.org/maven2/org/apache/pdfbox/pdfbox-app/3.0.7/pdfbox-app-3.0.7.jar
License and third-party notices are included in the JAR (`META-INF/LICENSE`, `META-INF/NOTICE`).
The common app build script embeds the dependency into the desktop JVM APK; no external PDF viewer is required.

JPEG2000 decoding uses `jai-imageio-core-1.4.0.jar` and `jai-imageio-jpeg2000-1.4.0.jar` from Maven Central under `com/github/jai-imageio/`. Their BSD-style licenses and the PDFBox distribution notices are preserved separately under `res/third-party/` in the APK to avoid merged JAR metadata overwriting notices.
