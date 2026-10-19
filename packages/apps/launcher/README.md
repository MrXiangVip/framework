# Launcher

Standalone application for this repository's desktop JVM framework.
Package name: `com.packages.launcher`.

From the repository root with JDK 8 or later:

```powershell
powershell -ExecutionPolicy Bypass -File packages/apps/launcher/build.ps1
```

On Linux (Bash 4.4 or later):

```bash
bash packages/apps/launcher/build.sh
```

Output: `out/system/app/launcher/launcher.apk`.
Framework dependencies compile separately into `out/framework/classes`.
Only application classes, the text manifest and `res/` enter the archive.

This is a JAR/ZIP package for the desktop JVM, not an installable Android APK.
It contains Java bytecode rather than DEX, and has no Android resource table or
Android APK signature. PMS scans the output directory and passes the APK path
through AMS/Zygote to ActivityThread, which loads application classes separately.
Run src/build.sh on Linux to build all packages/apps applications and start the framework. The standalone
application build scripts only build the package.
