"""Check real CEF decoding and distinct Chinese glyph pixels without system CJK fonts."""
import http.server
import os
from pathlib import Path
import tempfile
import threading

from browser_cef_smoke import Host, ROOT, display


class ChineseSite(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        charset = "gb18030" if self.path == "/gb18030" else "utf-8"
        # Different ideographs must produce different, nonempty canvas pixels, not identical tofu.
        html = '''<!doctype html><title>中文标题：浏览器测试</title>
<body style="background:white;font:32px sans-serif">中文网页：你好，世界！<input value="中文输入">
<script>
const canvas=document.createElement('canvas');canvas.width=64;canvas.height=64;
const g=canvas.getContext('2d');g.font='40px sans-serif';
const hashes=Array.from('中文国').map(c=>{
 g.clearRect(0,0,64,64);g.fillText(c,4,48);
 const pixels=g.getImageData(0,0,64,64).data;let hash=0,count=0;
 for(let i=3;i<pixels.length;i+=4){hash=(Math.imul(hash,31)+pixels[i])|0;if(pixels[i])count++;}
 return count>100?hash:0;
});
window.glyphsOK=new Set(hashes).size===3&&!hashes.includes(0);
document.title+='|'+(window.glyphsOK?'GLYPHS OK':'MISSING GLYPHS');
</script>
<button onclick="document.title=window.glyphsOK?'GLYPHS OK':'MISSING GLYPHS'"
 style="position:absolute;left:20px;top:160px;width:200px;height:50px">检查中文字形</button>'''
        data = html.encode(charset)
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=" + charset)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


def run():
    runtime = ROOT / "out/system/lib/browser"
    env = os.environ.copy()
    for name in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"):
        env.pop(name, None)
    env["LD_LIBRARY_PATH"] = str(runtime / "bin/lib/linux64")
    env["FONTCONFIG_FILE"] = str(runtime / "fonts.conf")
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), ChineseSite)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        with display() as screen, tempfile.TemporaryDirectory(prefix="browser-chinese-") as temporary:
            env["DISPLAY"] = screen
            host = Host(runtime, Path(temporary), env)
            try:
                host.wait(lambda: host.state["ready"], "Chinese test host starts")
                host.send("SIZE", 800, 600)
                for charset in ("utf-8", "gb18030"):
                    host.navigate("http://127.0.0.1:%d/%s" % (server.server_port, charset))
                    host.wait(lambda: host.state["url"].endswith("/" + charset) and host.state["title"].startswith("中文标题：浏览器测试|") and not host.state["loading"], charset + " Chinese page title survives decoding and IPC")
                    host.wait(lambda: host.state["title"].endswith("|GLYPHS OK"), charset + " Chinese characters render distinct glyphs")
            finally:
                host.close()
            assert host.process.returncode == 0
    finally:
        server.shutdown()


if __name__ == "__main__":
    run()
