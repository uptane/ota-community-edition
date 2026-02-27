#!/usr/bin/env python3
"""Serve /gui static files and proxy OTA API calls to avoid browser CORS issues."""

from __future__ import annotations

import argparse
import http.client
import mimetypes
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
GUI_DIR = ROOT / "gui"

HOP_BY_HOP = {
    "connection",
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "te",
    "trailers",
    "transfer-encoding",
    "upgrade",
    "host",
    "content-length",
}


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="GUI static server + OTA reverse proxy")
    p.add_argument("--port", type=int, default=8080)
    p.add_argument("--target", default="http://ota.ce", help="OTA base URL to proxy API/health requests")
    return p.parse_args()


class Handler(BaseHTTPRequestHandler):
    target = urlsplit("http://ota.ce")

    def do_GET(self):
        self._handle()

    def do_HEAD(self):
        self._handle(head_only=True)

    def do_POST(self):
        self._handle()

    def do_PUT(self):
        self._handle()

    def do_PATCH(self):
        self._handle()

    def do_DELETE(self):
        self._handle()

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.send_header("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS")
        self.end_headers()

    def _handle(self, head_only: bool = False):
        if self.path in ("/", ""):
            return self._serve_file(GUI_DIR / "index.html", head_only=head_only)
        if self.path.startswith("/gui/"):
            rel = self.path[len("/gui/") :].split("?", 1)[0]
            if not rel:
                rel = "index.html"
            return self._serve_file((GUI_DIR / rel).resolve(), head_only=head_only)
        # proxy everything else
        return self._proxy(head_only=head_only)

    def _serve_file(self, path: Path, head_only: bool = False):
        try:
            if not str(path).startswith(str(GUI_DIR.resolve())):
                raise FileNotFoundError
            data = path.read_bytes()
        except FileNotFoundError:
            self.send_error(404, "Not found")
            return

        content_type = mimetypes.guess_type(str(path))[0] or "application/octet-stream"
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        if not head_only:
            self.wfile.write(data)

    def _proxy(self, head_only: bool = False):
        length = int(self.headers.get("Content-Length", "0") or "0")
        body = self.rfile.read(length) if length else None

        target_host = self.target.hostname or "ota.ce"
        target_port = self.target.port or (443 if self.target.scheme == "https" else 80)
        conn_cls = http.client.HTTPSConnection if self.target.scheme == "https" else http.client.HTTPConnection

        path = self.path
        if self.target.path:
            base = self.target.path.rstrip("/")
            path = f"{base}{self.path}"

        headers = {k: v for k, v in self.headers.items() if k.lower() not in HOP_BY_HOP}
        headers["Host"] = target_host

        conn = conn_cls(target_host, target_port, timeout=30)
        try:
            conn.request(self.command, path, body=body, headers=headers)
            resp = conn.getresponse()
            payload = resp.read()

            self.send_response(resp.status)
            for k, v in resp.getheaders():
                if k.lower() in HOP_BY_HOP:
                    continue
                self.send_header(k, v)
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            if not head_only:
                self.wfile.write(payload)
        except Exception as exc:
            msg = f"Proxy error: {exc}".encode()
            self.send_response(502)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(msg)))
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            if not head_only:
                self.wfile.write(msg)
        finally:
            conn.close()


def main():
    args = parse_args()
    Handler.target = urlsplit(args.target)
    srv = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    print(f"Serving GUI on http://0.0.0.0:{args.port}/gui/ and proxying to {args.target}")
    srv.serve_forever()


if __name__ == "__main__":
    main()
