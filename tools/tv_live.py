#!/usr/bin/env python3
"""Live Android TV emulator view + D-pad remote over HTTP.

Serves the current emulator framebuffer as a multipart stream and exposes
buttons that map to `adb shell input keyevent` / `input tap`.

Run:  python3 tools/tv_live.py
Open: http://127.0.0.1:8765/
"""

import io
import os
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

from PIL import Image

ADB = os.environ.get("ADB", "adb")
SERIAL = os.environ.get("ANDROID_SERIAL", "emulator-5554")
PORT = int(os.environ.get("TV_LIVE_PORT", "8765"))

KEYCODES = {
    "up": 19,
    "down": 20,
    "left": 21,
    "right": 22,
    "ok": 23,
    "back": 4,
    "home": 3,
    "menu": 82,
    "search": 84,
    "playpause": 85,
    "next": 87,
    "prev": 88,
}

PAGE = """<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Android TV — Rezka TV</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin: 0; background: #07070a; color: #fff;
         font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
  .wrap { max-width: 1100px; margin: 0 auto; padding: 18px; }
  h1 { font-size: 18px; font-weight: 600; margin: 0 0 12px; letter-spacing: .02em; }
  h1 span { color: #ff4d5a; }
  .screen { width: 100%; aspect-ratio: 16/9; background: #000; border-radius: 10px;
            overflow: hidden; border: 1px solid #26262f; }
  .screen img { width: 100%; height: 100%; object-fit: contain; display: block; }
  .bar { display: flex; gap: 10px; flex-wrap: wrap; margin: 12px 0; }
  button { background: #1a1a22; color: #fff; border: 1px solid #34343f;
           border-radius: 9px; padding: 10px 14px; font-size: 14px; cursor: pointer; }
  button:hover { background: #24242e; }
  button.accent { background: #ff4d5a; border-color: #ff4d5a; color: #fff; font-weight: 600; }
  button.accent:hover { background: #ff626e; }
  .pad { display: grid; grid-template-columns: repeat(3, 76px); gap: 8px;
         justify-content: center; margin: 6px auto 14px; }
  .pad button { height: 56px; font-size: 20px; padding: 0; }
  .pad .blank { visibility: hidden; }
  .hint { color: #8b8b98; font-size: 12px; margin-top: 8px; }
  .status { color: #ff4d5a; font-size: 12px; min-height: 16px; }
</style>
</head>
<body>
<div class="wrap">
  <h1>ANDROID TV <span>LIVE</span> · REZKA</h1>
  <div class="screen"><img src="/stream.mjpg" alt="emulator screen"></div>
  <div class="bar">
    <button class="accent" onclick="cmd('/launch')">Запустить Rezka TV</button>
    <button onclick="cmd('/anime')">Открыть «Гравити Фолз»</button>
    <button onclick="cmd('/key?c=home')">Home</button>
    <button onclick="cmd('/key?c=menu')">Menu</button>
    <button onclick="cmd('/key?c=back')">Back</button>
  </div>
  <div class="pad">
    <div class="blank"></div><button onclick="cmd('/key?c=up')">▲</button><div class="blank"></div>
    <button onclick="cmd('/key?c=left')">◀</button>
    <button onclick="cmd('/key?c=ok')" style="background:#ff4d5a;border-color:#ff4d5a">OK</button>
    <button onclick="cmd('/key?c=right')">▶</button>
    <div class="blank"></div><button onclick="cmd('/key?c=down')">▼</button><div class="blank"></div>
  </div>
  <div class="bar" style="justify-content:center">
    <button onclick="cmd('/key?c=playpause')">⏯ Play/Pause</button>
    <button onclick="cmd('/key?c=prev')">⏮ Серия назад</button>
    <button onclick="cmd('/key?c=next')">⏭ Серия вперёд</button>
  </div>
  <div class="status" id="status"></div>
  <div class="hint">Клик по кнопкам отправляет keyevent в эмулятор. Экран обновляется ~2 раза в секунду.</div>
</div>
<script>
  async function cmd(url) {
    const s = document.getElementById('status');
    try {
      const r = await fetch(url);
      s.textContent = r.ok ? '' : 'ошибка';
    } catch (e) { s.textContent = 'нет связи с сервером'; }
  }
</script>
</body>
</html>
"""


def adb(*args, timeout=20):
    return subprocess.run([ADB, "-s", SERIAL, *args],
                          capture_output=True, timeout=timeout)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        pass

    def _send(self, code, ctype, body, extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        if extra:
            for k, v in extra.items():
                self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)

        if parsed.path == "/":
            self._send(200, "text/html; charset=utf-8", PAGE.encode())
            return

        if parsed.path == "/stream.mjpg":
            self.send_response(200)
            self.send_header("Content-Type", "multipart/x-mixed-replace; boundary=frame")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Connection", "close")
            self.end_headers()
            try:
                while True:
                    res = adb("exec-out", "screencap", "-p", timeout=15)
                    if res.returncode != 0 or not res.stdout:
                        time.sleep(0.6)
                        continue
                    try:
                        img = Image.open(io.BytesIO(res.stdout)).convert("RGB")
                        img.thumbnail((960, 540), Image.LANCZOS)
                        out = io.BytesIO()
                        img.save(out, "JPEG", quality=72, optimize=True)
                        frame = out.getvalue()
                        ctype = "image/jpeg"
                    except Exception:
                        frame = res.stdout
                        ctype = "image/png"
                    self.wfile.write(b"--frame\r\n")
                    self.wfile.write(("Content-Type: %s\r\n" % ctype).encode())
                    self.wfile.write(("Content-Length: %d\r\n\r\n" % len(frame)).encode())
                    self.wfile.write(frame)
                    self.wfile.write(b"\r\n")
                    self.wfile.flush()
                    time.sleep(0.35)
            except (BrokenPipeError, ConnectionResetError):
                pass
            return

        if parsed.path == "/key":
            name = (params.get("c") or [""])[0]
            code = KEYCODES.get(name)
            if code is None:
                self._send(400, "text/plain", b"unknown key")
                return
            adb("shell", "input", "keyevent", str(code))
            self._send(200, "text/plain", b"ok")
            return

        if parsed.path == "/tap":
            try:
                x = int((params.get("x") or ["0"])[0])
                y = int((params.get("y") or ["0"])[0])
            except ValueError:
                self._send(400, "text/plain", b"bad coordinates")
                return
            adb("shell", "input", "tap", str(x), str(y))
            self._send(200, "text/plain", b"ok")
            return

        if parsed.path == "/launch":
            adb("shell", "am", "start", "-n", "me.rezka.tv/.ui.MainActivity")
            self._send(200, "text/plain", b"ok")
            return

        if parsed.path == "/anime":
            adb("shell", "am", "start", "-a", "android.intent.action.VIEW",
                "-d", "https://rezka.ag/cartoons/comedy/1982-graviti-folz-2012.html",
                "me.rezka.tv")
            self._send(200, "text/plain", b"ok")
            return

        self._send(404, "text/plain", b"not found")


def main():
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"TV live view: http://127.0.0.1:{PORT}/  (serial {SERIAL})", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
