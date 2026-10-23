"""Build Java 8 app, verify actual APK and exercise X11 input on an isolated display."""
import os
from pathlib import Path
import subprocess
import tempfile
import sys
from browser_cef_smoke import display

ROOT = Path(__file__).resolve().parents[1]


def run():
    env = os.environ.copy()
    for name in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"):
        env.pop(name, None)
    jdk = Path(env.get("JDK_HOME", "/usr/lib/jvm/java-8-openjdk-amd64"))
    with tempfile.TemporaryDirectory(prefix="tonghuashun-test-") as temporary:
        work = Path(temporary)
        classes = work / "classes"
        classes.mkdir()
        sources = list((ROOT / "base/com/android").rglob("*.java"))
        sources.extend([ROOT / "tests/com/android/app/TonghuashunTest.java", ROOT / "tests/com/android/app/TonghuashunNativeTest.java",
                        ROOT / "tests/com/packages/tonghuashun/MarketStoreTest.java", ROOT / "tests/com/packages/tonghuashun/QuoteTest.java",
                        ROOT / "tests/com/android/app/TonghuashunLiveTest.java", ROOT / "packages/apps/tonghuashun/src/com/packages/tonghuashun/MarketStore.java",
                        ROOT / "packages/apps/tonghuashun/src/com/packages/tonghuashun/QuoteClient.java", ROOT / "packages/apps/tonghuashun/src/com/packages/tonghuashun/QuoteFeed.java"])
        def command(args, **kwargs):
            subprocess.run(list(map(str, args)), cwd=ROOT, env=env, check=True, timeout=90, **kwargs)
        command([jdk / "bin/javac", "-encoding", "UTF-8", "-cp", ROOT / "libs/*", "-h", work, "-d", classes, *sources])
        command(["bash", ROOT / "packages/apps/build.sh", "--framework-classes", classes, "tonghuashun"])
        cp = str(classes) + ":" + str(ROOT / "libs/*")
        command([jdk / "bin/java", "-cp", cp, "com.packages.tonghuashun.MarketStoreTest", work / "model"])
        command([jdk / "bin/java", "-cp", cp, "com.packages.tonghuashun.QuoteTest", work / "quotes/account.properties", *(["--live"] if "--live" in sys.argv else [])])
        previews = ROOT / "out/tonghuashun-previews"
        previews.mkdir(exist_ok=True)
        # A fresh account isolates each run while screenshot files stay available for review.
        command([jdk / "bin/java", "-Djava.awt.headless=true", "-cp", cp, "com.android.app.TonghuashunTest", work / "ui"])
        import shutil
        for png in (work / "ui").glob("*.png"):
            shutil.copy2(png, previews / png.name)
        command(["gcc", "-fPIC", "-shared", "-I" + str(jdk / "include"), "-I" + str(jdk / "include/linux"),
                 "-I" + str(work), "-o", work / "libwindow.so", ROOT / "base/com/android/XWindow.c", "-lX11"])
        command(["gcc", "-Wall", "-Wextra", "-Werror", "-o", work / "driver", ROOT / "tests/x11_browser_driver.c", "-lX11"])
        with display() as screen:
            env["DISPLAY"] = screen
            command([jdk / "bin/java", "-Djava.awt.headless=true", "-Djava.library.path=" + str(work), "-cp", cp,
                     "com.android.app.TonghuashunNativeTest", work / "driver", work / "native-account.properties"])
            if "--live" in sys.argv:
                command([jdk / "bin/java", "-Djava.awt.headless=true", "-Djava.library.path=" + str(work), "-cp", cp,
                         "com.android.app.TonghuashunLiveTest", work / "live-account.properties"] )
        print("PASS: Linux Java 8 APK and X11 integration; previews:", previews)


if __name__ == "__main__":
    run()
