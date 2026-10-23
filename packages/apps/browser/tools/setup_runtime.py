#!/usr/bin/env python3
"""Install a pinned Linux CEF/JCEF runtime, without altering system packages."""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import time
import urllib.request

TAG = "1.0.71"
ASSET = "linux-amd64.tar.gz"
PIN_SIZE = 157891982
PIN_SHA256 = "58bfdafcf98529d02a04f80741620e1f03c2a61724bdab76702869ac4800e7f0"


def request(url, **headers):
    return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "FrameworkBrowser/1", **headers}), timeout=60)


def download(cache):
    cached = cache / ASSET
    origin = "https://github.com/jcefmaven/jcefbuild/releases/download/" + TAG + "/" + ASSET
    if cached.is_file() and cached.stat().st_size == PIN_SIZE and hashlib.sha256(cached.read_bytes()).hexdigest() == PIN_SHA256:
        return cached, PIN_SHA256, origin
    metadata = json.load(request("https://api.github.com/repos/jcefmaven/jcefbuild/releases/tags/" + TAG))
    asset = next(a for a in metadata["assets"] if a["name"] == ASSET)
    target = cache / ASSET
    if not target.exists() or target.stat().st_size != asset["size"]:
        # Range requests resume independently; one failed connection does not restart 150MB.
        with request(asset["browser_download_url"]) as response:
            url = response.url
        chunk_size = 2 * 1024 * 1024
        chunks = [(i, start, min(start + chunk_size, asset["size"]) - 1)
                  for i, start in enumerate(range(0, asset["size"], chunk_size))]

        def fetch(chunk):
            i, start, end = chunk
            part = cache / (ASSET + ".part-%03d" % i)
            if part.exists() and part.stat().st_size == end - start + 1:
                return part
            for attempt in range(5):
                try:
                    with request(url, Range="bytes=%d-%d" % (start, end)) as source:
                        if source.status != 206:
                            raise RuntimeError("Server did not honor a range request")
                        with part.open("wb") as output:
                            shutil.copyfileobj(source, output)
                    if part.stat().st_size != end - start + 1:
                        raise RuntimeError("Incomplete download chunk")
                    print("Downloaded chunk %d/%d" % (i + 1, len(chunks)), flush=True)
                    return part
                except Exception:
                    if attempt == 4:
                        raise
                    time.sleep(min(2 ** attempt, 8))

        with concurrent.futures.ThreadPoolExecutor(max_workers=12) as executor:
            parts = list(executor.map(fetch, chunks))
        temporary = target.with_suffix(".tmp")
        with temporary.open("wb") as output:
            for part in parts:
                with part.open("rb") as source:
                    shutil.copyfileobj(source, output)
        temporary.replace(target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    expected = asset.get("digest")
    if expected and expected != "sha256:" + digest:
        raise RuntimeError("Runtime SHA-256 does not match the GitHub release digest")
    return target, digest, asset["browser_download_url"]


def host_digest(root):
    # Recompile only when the host sources or shared IPC protocol change.
    sources = sorted((root / "hostsrc").rglob("*.java"))
    sources.append(root / "src/com/packages/browser/BrowserProtocol.java")
    sources.extend(sorted((root / "res/fonts").glob("*")))
    digest = hashlib.sha256()
    for source in sources:
        digest.update(str(source.relative_to(root)).encode())
        digest.update(source.read_bytes().replace(b"\r\n", b"\n"))
    digest.update(Path(__file__).read_bytes().replace(b"\r\n", b"\n"))
    return digest.hexdigest()


def installation_current(root, runtime):
    try:
        metadata = json.loads((runtime / "runtime.json").read_text())
        if metadata.get("tag") != TAG or metadata.get("sha256") != PIN_SHA256 or metadata.get("host_sources_sha256") != host_digest(root):
            return False
        required = ["host.jar", "bin/jcef.jar", "bin/lib/linux64/libcef.so", "bin/lib/linux64/libjcef.so",
                    "bin/lib/linux64/resources.pak", "bin/lib/linux64/jcef_helper", "fonts.conf",
                    "fonts/NotoSansCJKsc-Regular.otf", "fonts/NotoEmoji-Regular.ttf"]
        if not all((runtime / name).is_file() for name in required):
            return False
        java = Path((runtime / "java.path").read_text().strip())
        configured = os.environ.get("BROWSER_JAVA_HOME")
        if configured and java != Path(configured) / "bin/java":
            return False
        return os.access(java, os.X_OK) and os.access(runtime / "bin/lib/linux64/jcef_helper", os.X_OK)
    except (OSError, ValueError):
        return False

def install(root, runtime, cache, archive=None):
    if not hasattr(os, "uname") or os.uname().sysname != "Linux" or os.uname().machine != "x86_64":
        raise RuntimeError("The pinned runtime currently supports Linux x86_64")
    cache.mkdir(parents=True, exist_ok=True)
    if archive:
        archive = Path(archive).resolve()
        digest, origin = hashlib.sha256(archive.read_bytes()).hexdigest(), "local archive"
    else:
        archive, digest, origin = download(cache)
    if digest != PIN_SHA256:
        raise RuntimeError("Runtime does not match the pinned JCEF release SHA-256")
    extraction = cache / "extracted"
    extraction.mkdir(exist_ok=True)
    with tarfile.open(archive, "r:gz") as package:
        # Extract regular files only; this also supports Python 3.8 without tarfile's newer filter API.
        for member in package.getmembers():
            resolved = (extraction / member.name).resolve()
            if os.path.commonpath([str(extraction.resolve()), str(resolved)]) != str(extraction.resolve()):
                raise RuntimeError("Unsafe archive path")
            if member.isdir():
                resolved.mkdir(parents=True, exist_ok=True)
            elif member.isfile():
                resolved.parent.mkdir(parents=True, exist_ok=True)
                with package.extractfile(member) as source, resolved.open("wb") as output:
                    shutil.copyfileobj(source, output)
                resolved.chmod(member.mode & 0o777)
            else:
                raise RuntimeError("Runtime archive contains unsupported links or special files")
    jars = list(extraction.rglob("jcef.jar"))
    if len(jars) != 1:
        raise RuntimeError("Archive must contain one jcef.jar")
    bin_dir = jars[0].parent
    runtime.mkdir(parents=True, exist_ok=True)
    shutil.copytree(bin_dir, runtime / "bin", dirs_exist_ok=True)
    native = next((runtime / "bin").rglob("libcef.so")).parent
    # Normalize distribution layouts so the app does not depend on archive nesting.
    expected = runtime / "bin/lib/linux64"
    if native != expected:
        expected.mkdir(parents=True, exist_ok=True)
        shutil.copytree(native, expected, dirs_exist_ok=True)
    # Relative paths keep the private font installation relocatable with the runtime.
    shutil.copytree(root / "res/fonts", runtime / "fonts", dirs_exist_ok=True)
    (runtime / "fonts.conf").write_text('''<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd">
<fontconfig>
  <include ignore_missing="yes">/etc/fonts/fonts.conf</include>
  <dir prefix="relative">fonts</dir>
  <cachedir prefix="xdg">fontconfig</cachedir>
  <alias><family>sans-serif</family><prefer><family>Noto Sans CJK SC</family></prefer></alias>
  <alias><family>emoji</family><prefer><family>Noto Emoji</family></prefer></alias>
</fontconfig>
''', encoding="utf-8")
    java_home = Path(os.environ.get("BROWSER_JAVA_HOME", "/usr/lib/jvm/java-17-openjdk-amd64"))
    if not (java_home / "bin/javac").is_file():
        raise RuntimeError("Install openjdk-17-jdk and openjdk-17-jre, or set BROWSER_JAVA_HOME to a full JDK 17+")
    (runtime / "java.path").write_text(str(java_home / "bin/java"))
    classes = cache / "host-classes"
    classes.mkdir(exist_ok=True)
    sources = list((root / "hostsrc").rglob("*.java"))
    sources.append(root / "src/com/packages/browser/BrowserProtocol.java")
    subprocess.run([str(java_home / "bin/javac"), "-encoding", "UTF-8", "-source", "8", "-target", "8", "-cp", str(runtime / "bin/*"),
                    "-d", str(classes), *map(str, sources)], check=True)
    subprocess.run([str(java_home / "bin/jar"), "cf", str(runtime / "host.jar"), "-C", str(classes), "."], check=True)
    for executable in expected.glob("jcef_helper*"):
        executable.chmod(executable.stat().st_mode | 0o111)
    (runtime / "runtime.json").write_text(json.dumps({"source": origin, "tag": TAG, "sha256": digest, "host_sources_sha256": host_digest(root)}, indent=2))
    for license_file in extraction.rglob("LICENSE*"):
        if license_file.is_file():
            (runtime / "licenses").mkdir(exist_ok=True)
            shutil.copy2(license_file, runtime / "licenses" / license_file.name)
    print("Installed browser runtime:", runtime, flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--runtime", type=Path)
    parser.add_argument("--cache", type=Path)
    parser.add_argument("--archive", type=Path)
    parser.add_argument("--ensure", action="store_true", help="Reuse a complete runtime when its host sources match")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    repo = root.parents[2]
    runtime = args.runtime or repo / "out/system/lib/browser"
    if args.ensure and installation_current(root, runtime):
        print("Browser runtime is ready:", runtime, flush=True)
    else:
        install(root, runtime, args.cache or repo / "out/browser-cef-cache", args.archive)
