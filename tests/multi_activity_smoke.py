"""Build an isolated two-Activity APK and exercise process reuse through real AMS/Zygote/X11."""
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

from browser_cef_smoke import display

ROOT = Path(__file__).resolve().parents[1]


def run():
    jdk = Path(os.environ.get("JDK_HOME", "/usr/lib/jvm/java-8-openjdk-amd64"))
    with tempfile.TemporaryDirectory(prefix="framework-multi-") as temporary:
        work = Path(temporary)
        classes, native, app_classes = (work / name for name in ("classes", "native", "app-classes"))
        for directory in (classes, native, app_classes):
            directory.mkdir()
        sources = list((ROOT / "base/com/android").rglob("*.java"))
        sources += [ROOT / "tests/com/android/app/MultiActivityIntegrationTest.java",
                    ROOT / "tests/com/android/view/WindowCloseLifecycleTest.java"]
        subprocess.run([str(jdk / "bin/javac"), "-encoding", "UTF-8", "-cp", str(ROOT / "libs/*"),
                        "-h", str(native), "-d", str(classes), *map(str, sources)], check=True)
        fixture = ROOT / "tests/fixtures/multi_activity"
        subprocess.run([str(jdk / "bin/javac"), "-cp", str(classes), "-d", str(app_classes),
                        *map(str, fixture.glob("*.java"))], check=True)
        apk = work / "root/out/system/app/multi/multi.apk"
        apk.parent.mkdir(parents=True)
        with zipfile.ZipFile(apk, "w") as archive:
            archive.write(fixture / "AndroidManifest.xml", "AndroidManifest.xml")
            for compiled in app_classes.rglob("*.class"):
                archive.write(compiled, compiled.relative_to(app_classes))
        subprocess.run(["gcc", "-fPIC", "-shared", "-I" + str(jdk / "include"),
                        "-I" + str(jdk / "include/linux"), "-I" + str(native),
                        "-o", str(native / "libwindow.so"), str(ROOT / "base/com/android/XWindow.c"), "-lX11"], check=True)
        driver = native / "driver"
        subprocess.run(["gcc", "-o", str(driver), str(ROOT / "tests/x11_browser_driver.c"), "-lX11"], check=True)
        env = os.environ.copy()
        for name in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"):
            env.pop(name, None)
        command = [str(jdk / "bin/java"), "-Djava.awt.headless=true", "-Djava.library.path=" + str(native),
                   "-cp", str(classes) + ":" + str(ROOT / "libs/*")]
        subprocess.run([*command, "com.android.view.WindowCloseLifecycleTest"], env=env, check=True, timeout=10)
        with display() as screen:
            env["DISPLAY"] = screen
            with (work / "integration.log").open("w+") as log:
                result = subprocess.run([*command, "com.android.app.MultiActivityIntegrationTest",
                                         str(work / "root"), str(work / "journal"), str(driver)],
                                        env=env, stdout=log, stderr=subprocess.STDOUT, timeout=65)
                log.seek(0)
                output = log.read()
            if result.returncode:
                raise AssertionError(output[-16000:])
            print("\n".join(line for line in output.splitlines()
                            if line.startswith("PASS:") or line.endswith("Test passed")))


if __name__ == "__main__":
    run()
