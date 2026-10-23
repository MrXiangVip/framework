"""Real CEF subprocess, JavaScript, Unicode input, navigation and OSR pixel checks."""
import base64
import contextlib
import http.server
import os
from pathlib import Path
import queue
import select
import shutil
import struct
import subprocess
import tempfile
import threading
import time

ROOT = Path(__file__).resolve().parents[1]
MAGIC = 0x42525731


@contextlib.contextmanager
def display():
    read_fd, write_fd = os.pipe()
    with tempfile.TemporaryFile() as log:
        process = subprocess.Popen(["Xvfb", "-displayfd", str(write_fd), "-screen", "0", "1280x900x24", "-nolisten", "unix", "-listen", "tcp", "-ac"],
                                   pass_fds=(write_fd,), stdout=log, stderr=log)
        os.close(write_fd)
        try:
            if not select.select([read_fd], [], [], 10)[0]:
                raise AssertionError("Xvfb did not start")
            number = os.read(read_fd, 32).decode().strip()
            if not number:
                raise AssertionError("Xvfb failed")
            yield "127.0.0.1:" + number
        finally:
            os.close(read_fd)
            process.terminate()
            process.wait(timeout=5)


class Site(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/two":
            html = '<title>SECOND PAGE</title><body style="background:#13b877">Second page</body>'
        else:
            html = '''<!doctype html><meta charset="utf-8"><title>FIRST PAGE</title>
<style>body{margin:0;background:rgb(15,100,180);font:20px sans-serif}button,input,select,a{position:absolute;left:30px;width:250px;height:44px}button{top:25px}input{top:100px}select{top:160px}a{top:220px;color:white}#bottom{position:absolute;top:1100px;height:1000px;width:100%;background:rgb(20,190,90)}</style>
<button onclick="document.title=event.ctrlKey?'CTRL CLICKED':'CLICKED'">Click me</button>
<input id="input" oninput="document.title='TEXT:'+Array.from(this.value).map(c=>c.codePointAt(0).toString(16)).join(',')">
<select><option>First option</option><option>Second option</option></select>
<a href="/two" target="_blank" onclick="document.title='LINK CLICKED'">Open next page</a><div id="bottom">Bottom</div>
<script>document.title='JS READY';window.oncontextmenu=e=>{e.preventDefault();document.title='RIGHT CLICKED'};window.onscroll=()=>{document.title='SCROLL:'+window.scrollY}</script>'''
        data = html.encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


class Host:
    def __init__(self, runtime, folder, env):
        self.events = queue.Queue()
        self.frame = None
        self.state = {"title": "", "url": "", "error": "", "ready": False}
        self.log_path = folder / "host.log"
        self.log = self.log_path.open("w")
        self.process = subprocess.Popen([(runtime / "java.path").read_text().strip(), "-Djava.awt.headless=false",
            "-Djava.library.path=" + str(runtime / "bin/lib/linux64"), "-cp", str(runtime / "host.jar") + ":" + str(runtime / "bin/*"),
            "com.packages.browser.host.CefHost", str(runtime), str(folder)], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=self.log, env=env)
        threading.Thread(target=self.read, daemon=True).start()

    def read(self):
        def exact(length):
            result = bytearray()
            while len(result) < length:
                data = self.process.stdout.read(length - len(result))
                if not data:
                    raise EOFError()
                result.extend(data)
            return bytes(result)
        try:
            while True:
                magic, kind, length = struct.unpack(">III", exact(12))
                assert magic == MAGIC and length <= 4096 * 2160 * 4 + 8
                payload = exact(length)
                if kind == 2:
                    w, h = struct.unpack(">II", payload[:8])
                    assert len(payload) == 8 + w * h * 4
                    self.frame = (w, h, payload[8:])
                elif kind == 3:
                    values = payload.decode().split("\t")
                    self.state = dict(zip(("url", "title", "error"), (base64.b64decode(x).decode() for x in values[:3])))
                    self.state.update(loading=values[3] == "1", back=values[4] == "1", forward=values[5] == "1", ready=values[6] == "1")
                self.events.put(kind)
        except BaseException as error:
            self.events.put(error)

    def send(self, *fields):
        payload = "\t".join(map(str, fields)).encode()
        self.process.stdin.write(struct.pack(">III", MAGIC, 1, len(payload)) + payload)
        self.process.stdin.flush()

    def wait(self, predicate, description, timeout=20):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if predicate():
                print("PASS:", description, flush=True)
                return
            try:
                event = self.events.get(timeout=min(.25, max(.01, deadline - time.monotonic())))
                if isinstance(event, BaseException):
                    break
            except queue.Empty:
                if self.process.poll() is not None:
                    break
        raise AssertionError(description + "\n" + str(self.state) + "\n" + self.log_path.read_text(errors="replace")[-12000:])

    def navigate(self, url):
        self.send("NAV", base64.b64encode(url.encode()).decode())

    def click(self, x, y):
        self.send("FOCUS", 1)
        self.send("MOUSE", 0, x, y, 1, 0)
        self.send("MOUSE", 1, x, y, 1, 0)

    def close(self):
        if self.process.stdin and not self.process.stdin.closed:
            self.process.stdin.close()
        try:
            self.process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            self.process.kill()
            self.process.wait(timeout=5)
            raise AssertionError("CEF host did not close when parent pipe closed")
        finally:
            self.log.close()


def run():
    runtime = ROOT / "out/system/lib/browser"
    environment = os.environ.copy()
    for key in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"):
        environment.pop(key, None)
    environment["LD_LIBRARY_PATH"] = str(runtime / "bin/lib/linux64")
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Site)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        with display() as screen, tempfile.TemporaryDirectory(prefix="browser-cef-check-") as temporary:
            environment["DISPLAY"] = screen
            host = Host(runtime, Path(temporary), environment)
            try:
                host.wait(lambda: host.state["ready"], "CEF creates an offscreen browser")
                host.send("SIZE", 800, 600)
                base = "http://127.0.0.1:%d" % server.server_port
                host.navigate(base + "/one")
                host.wait(lambda: host.state["title"] == "JS READY", "real Chromium executes JavaScript")
                host.wait(lambda: host.frame is not None and host.frame[:2] == (800, 600)
                          and host.frame[2][(300 * 800 + 700) * 4:(300 * 800 + 700) * 4 + 3] == bytes((180, 100, 15)),
                          "CSS background arrives as actual BGRA pixels")
                host.click(80, 45)
                host.wait(lambda: host.state["title"] == "CLICKED", "mouse click activates a DOM button")
                host.click(100, 120)
                for ch in "中文😀":
                    host.send("KEY", 0, ord(ch), 0)
                host.wait(lambda: host.state["title"] == "TEXT:4e2d,6587,1f600", "committed Chinese and emoji reach webpage input")
                host.send("KEY", 1, 0xff08, 0)
                host.send("KEY", 2, 0xff08, 0)
                host.wait(lambda: host.state["title"] == "TEXT:4e2d,6587", "Backspace edits webpage text")
                host.click(80, 240)
                host.wait(lambda: host.state["title"] == "SECOND PAGE" and host.state["url"].endswith("/two"), "target=_blank opens in the current single tab")
                host.send("BACK")
                host.wait(lambda: host.state["url"].endswith("/one") and host.state["forward"], "back navigation restores previous page")
                host.send("FORWARD")
                host.wait(lambda: host.state["title"] == "SECOND PAGE", "forward navigation restores next page")
                host.navigate(base + "/one")
                host.wait(lambda: host.state["title"] == "JS READY", "navigate back to test document")
                # A title can arrive before Blink lays out the new document; wait for its actual frame.
                host.wait(lambda: host.frame is not None and host.frame[2][(300 * host.frame[0] + 700) * 4:(300 * host.frame[0] + 700) * 4 + 3] == bytes((180, 100, 15)) and not host.state["loading"], "new document finishes layout before wheel input")
                host.send("FOCUS", 1)
                host.send("MOUSE", 5, 700, 300, 1, 0)
                for _ in range(6):
                    host.send("WHEEL", 700, 300, -120, 0)
                host.wait(lambda: host.state["title"].startswith("SCROLL:") and int(host.state["title"].split(":")[1]) > 0, "wheel scrolls webpage content")
                host.send("SIZE", 640, 480)
                host.wait(lambda: host.frame is not None and host.frame[:2] == (640, 480), "resize produces a newly sized webpage frame")
                host.navigate("http://127.0.0.1:1/unavailable")
                host.wait(lambda: bool(host.state["error"]), "network failures are reported to browser UI")
            finally:
                host.close()
            assert host.process.returncode == 0, host.log_path.read_text(errors="replace")
            print("PASS: closing parent pipe shuts down CEF cleanly", flush=True)
    finally:
        server.shutdown()


if __name__ == "__main__":
    run()
