"""Runtime entries must be bundled; incomplete builds must preserve the installed APK."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

repo = Path(__file__).resolve().parents[1]
required = (repo / "packages/apps/mqchat/required-apk-entries.txt").read_text().splitlines()
with zipfile.ZipFile(repo / "out/system/app/mqchat/mqchat.apk") as apk:
    assert all(entry in apk.namelist() for entry in required if entry), "SQLite runtime missing"

with tempfile.TemporaryDirectory(prefix="mqchat-packaging-") as temporary:
    fixture = Path(temporary)
    apps = fixture / "packages/apps"
    probe = apps / "probe"
    (probe / "src").mkdir(parents=True)
    (probe / "AndroidManifest.xml").write_text('<manifest package="test.probe"/>')
    (probe / "src/Probe.java").write_text("public class Probe {}")
    (probe / "required-apk-entries.txt").write_text("org/sqlite/JDBC.class\n")
    installed = fixture / "out/system/app/probe/probe.apk"
    installed.parent.mkdir(parents=True)
    installed.write_bytes(b"preserve-installed-apk")
    framework = str(repo / "out/framework/classes")
    if os.name == "nt":
        script = apps / "build.ps1"
        shutil.copyfile(repo / "packages/apps/build.ps1", script)
        command = ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(script), "probe", "-FrameworkClasses", framework]
    else:
        script = apps / "build.sh"
        shutil.copyfile(repo / "packages/apps/build.sh", script)
        command = ["bash", str(script), "--framework-classes", framework, "probe"]
    result = subprocess.run(command, capture_output=True)
    assert result.returncode != 0, "Incomplete APK incorrectly accepted"
    assert b"org/sqlite/JDBC.class" in result.stdout + result.stderr, result.stderr
    assert installed.read_bytes() == b"preserve-installed-apk", "Installed APK was overwritten"
print("PASS: bundled SQLite runtime; incomplete build rejected; existing APK preserved")
