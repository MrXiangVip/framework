"""Run the real frontend APK and native X11 surface against an isolated local website."""
import http.server
import os
from pathlib import Path
import subprocess
import threading
from browser_cef_smoke import Site, display

ROOT = Path(__file__).resolve().parents[1]


def run():
    os.chdir(ROOT)
    classes = ROOT / "out/browser-integration-classes"
    native = ROOT / "out/browser-integration-native"
    classes.mkdir(parents=True, exist_ok=True)
    native.mkdir(parents=True, exist_ok=True)
    jdk = Path(os.environ.get("JDK_HOME", "/usr/lib/jvm/java-8-openjdk-amd64"))
    sources = list((ROOT / "base/com/android").rglob("*.java"))
    sources.append(ROOT / "tests/com/android/app/BrowserIntegrationTest.java")
    subprocess.run([str(jdk / "bin/javac"), "-encoding", "UTF-8", "-cp", str(ROOT / "libs/*"),
                    "-h", str(native), "-d", str(classes), *map(str, sources)], check=True)
    subprocess.run(["gcc", "-fPIC", "-shared", "-I" + str(jdk / "include"), "-I" + str(jdk / "include/linux"),
                    "-I" + str(native), "-o", str(native / "libwindow.so"), str(ROOT / "base/com/android/XWindow.c"), "-lX11"], check=True)
    subprocess.run(["gcc", "-o", str(native / "driver"), str(ROOT / "tests/x11_browser_driver.c"), "-lX11"], check=True)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Site)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        with display() as screen:
            env = os.environ.copy()
            env["DISPLAY"] = screen
            for name in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"):
                env.pop(name, None)
            with (ROOT / "out/browser-app-integration.log").open("w") as log:
                result = subprocess.run([str(jdk / "bin/java"), "-Djava.awt.headless=true", "-Djava.library.path=" + str(native),
                    "-cp", str(classes) + ":" + str(ROOT / "libs/*"), "com.android.app.BrowserIntegrationTest",
                    "http://127.0.0.1:%d/one" % server.server_port], env=env, stdout=log, stderr=subprocess.STDOUT, timeout=55)
            text = (ROOT / "out/browser-app-integration.log").read_text(errors="replace")
            print("\n".join(line for line in text.splitlines() if line.startswith("PASS:")))
            if result.returncode:
                raise AssertionError(text[-12000:])
    finally:
        server.shutdown()


if __name__ == "__main__":
    run()
