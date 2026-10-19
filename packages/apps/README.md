# Application builds

`src/build.sh` automatically discovers and packages all direct subdirectories of
`packages/apps` that contain `AndroidManifest.xml`. The already compiled framework
is reused. Each application must provide `src/`; `res/` is included when present.

Build all applications without starting the framework:

```sh
bash packages/apps/build.sh
```

Build selected applications:

```sh
bash packages/apps/build.sh settings camera
```

PowerShell equivalents:

```powershell
powershell -File packages/apps/build.ps1
powershell -File packages/apps/build.ps1 settings camera
```

Outputs are `out/system/app/<directory>/<directory>.apk`, which PMS already scans.
Every APK contains only its own freshly compiled classes, text manifest and
resources. Framework classes are compiled once per invocation and remain outside
the APKs. Compilation or packaging failures stop the build. Successfully packaged
APKs replace their previous output only after packaging completes.

The existing launcher/build.sh and launcher/build.ps1 remain single-app entry
points. Adding another app requires no build-script changes: create a directory
with its manifest and Java sources, then run the normal build again. Removing an
app directory does not uninstall an existing APK under out/system/app.
