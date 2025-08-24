#!/usr/bin/env python3
"""Simple local image upload HTTP server.

Features:
 - Serves an HTML form at /
 - Accepts POST uploads (multipart/form-data) for image files (jpg/jpeg/png/gif/webp/bmp)
 - Saves into a local folder (default: ./uploaded_images) creating it if missing
 - Lists previously uploaded images with thumbnails (browser-scaled)

Usage:
  python upload_server.py --port 8080 --dir ./my_uploads
  # alias also supported:
  python upload_server.py --folder ./some_folder

Environment overrides:
  UPLOAD_PORT, UPLOAD_DIR

NOT FOR PRODUCTION: no auth, minimal validation.
"""
from __future__ import annotations
import argparse
import html
import os
import posixpath
import sys
import time
from http import HTTPStatus
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from typing import List, Tuple, Optional
import urllib.parse  # added for URL encoding

ALLOWED_EXT = {"jpg", "jpeg", "png", "gif", "bmp", "webp"}
MAX_BYTES = 25 * 1024 * 1024  # 25 MB per request

# --- Translations (basic i18n) ---
TRANSLATIONS = {
    'en': {
        'title': 'GlideWall Image Uploader',
        'no_images': 'No images uploaded yet.',
        'allowed_notice': 'Allowed: {ext} • Max {size}MB per request • Tap a thumbnail to open full size.',
        'heading_files_suffix': '{count} file(s)',
        'upload_btn': 'Upload',
        'choose_files_aria': 'Choose image files to upload',
        'upload_btn_aria': 'Upload selected images',
        'footer': '{count} file(s) • Mobile-friendly layout',
    },
    'fr': {
        'title': 'Téléverseur d’images GlideWall',
        'no_images': 'Aucune image téléversée pour l’instant.',
        'allowed_notice': 'Formats autorisés : {ext} • Max {size}Mo par envoi • Touchez une vignette pour l’ouvrir.',
        'heading_files_suffix': '{count} fichier{plural}',
        'upload_btn': 'Envoyer',
        'choose_files_aria': 'Sélectionner des images à téléverser',
        'upload_btn_aria': 'Téléverser les images sélectionnées',
        'footer': '{count} fichier{plural} • Affichage mobile',
    }
}

def pick_lang(path: str, headers) -> str:
    # URL query ?lang=fr overrides, else Accept-Language
    q = ''
    if '?' in path:
        q = path.split('?', 1)[1]
    for part in q.split('&'):
        if part.lower().startswith('lang='):
            val = part.split('=', 1)[1].lower()
            if val in TRANSLATIONS:
                return val
    accept = headers.get('Accept-Language', '') if headers else ''
    if accept:
        if accept.lower().startswith('fr'):
            return 'fr'
    return 'en'

# -------------- Utility helpers --------------

def safe_filename(name: str) -> str:
    name = os.path.basename(name or "")
    name = name.replace('\x00', '')
    cleaned = []
    for ch in name:
        if ch.isalnum() or ch in ('-', '_', '.',):
            cleaned.append(ch)
        elif ch == ' ':
            cleaned.append('_')
    fn = ''.join(cleaned) or 'file'
    # Keep only single dot extension part
    if fn.count('.') > 1:
        base, ext = fn.rsplit('.', 1)
        fn = base.replace('.', '_') + '.' + ext
    ext = fn.rsplit('.', 1)[-1].lower() if '.' in fn else ''
    if ext not in ALLOWED_EXT:
        fn += '.png'
    return fn

def is_allowed_image(path: str) -> bool:
    ext = path.rsplit('.', 1)[-1].lower() if '.' in path else ''
    return ext in ALLOWED_EXT

# Minimal image signature sniffing (fallback only; not exhaustive)
# Returns a canonical lowercase extension WITHOUT leading dot or None.

def detect_image(data: bytes) -> Optional[str]:
    if len(data) < 12:
        return None
    # JPEG
    if data.startswith(b"\xFF\xD8\xFF"):
        return "jpg"
    # PNG
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png"
    # GIF
    if data.startswith(b"GIF87a") or data.startswith(b"GIF89a"):
        return "gif"
    # BMP
    if data.startswith(b"BM"):
        return "bmp"
    # WEBP (RIFF container with WEBP)
    if data.startswith(b"RIFF") and b"WEBP" in data[8:16]:
        return "webp"
    return None

# --------- Multipart parsing (lightweight, no external deps) ---------
# We implement a simple parser for multipart/form-data limited to our needs.
# It reads the full body into memory (bounded by MAX_BYTES) and splits by boundary.
# Returns list of (headers_dict, content_bytes).

def parse_multipart(body: bytes, boundary: str) -> List[Tuple[dict, bytes]]:
    parts: List[Tuple[dict, bytes]] = []
    if not boundary:
        return parts
    delim = b"--" + boundary.encode('utf-8')
    end_delim = delim + b"--"
    segments = body.split(delim)
    for seg in segments:
        if not seg or seg in (b"--", b"--\r\n", b"\r\n", b"\n"):
            continue
        if seg.startswith(b"--"):
            # end marker
            break
        # Strip leading CRLF
        if seg.startswith(b"\r\n"):
            seg = seg[2:]
        header_blob, _, content = seg.partition(b"\r\n\r\n")
        if not _:
            continue
        # Trim final CRLF that precedes next boundary
        if content.endswith(b"\r\n"):
            content = content[:-2]
        headers = {}
        for line in header_blob.split(b"\r\n"):
            if b":" in line:
                k, v = line.split(b":", 1)
                headers[k.decode('utf-8').strip().lower()] = v.decode('utf-8').strip()
        parts.append((headers, content))
    return parts

# Extract filename and field name from Content-Disposition header.

def parse_content_disposition(value: str) -> Tuple[Optional[str], Optional[str]]:
    # value like: form-data; name="files"; filename="example.png"
    if not value:
        return None, None
    segments = [seg.strip() for seg in value.split(';')]
    field = None
    filename = None
    for seg in segments:
        if '=' in seg:
            k, v = seg.split('=', 1)
            v = v.strip().strip('"')
            lk = k.lower()
            if lk == 'name':
                field = v
            elif lk == 'filename':
                filename = v
    return field, filename

# -------------- HTTP Handler --------------

class UploadHandler(SimpleHTTPRequestHandler):
    server_version = "UploadHTTP/0.2"

    def __init__(self, *args, upload_dir: str, **kwargs):
        self.upload_dir = upload_dir
        self._script_dir = os.path.dirname(os.path.abspath(__file__))  # for serving viewer.js
        super().__init__(*args, directory=upload_dir, **kwargs)

    def do_GET(self):  # noqa: N802
        # Serve our viewer script explicitly if requested.
        if self.path == '/viewer.js':
            return self._serve_viewer_js()
        if self.path == '/' or self.path.startswith('/?'):
            self._serve_index()
        else:
            super().do_GET()

    def _serve_viewer_js(self):
        path = os.path.join(self._script_dir, 'viewer.js')
        try:
            with open(path, 'rb') as f:
                data = f.read()
            self.send_response(HTTPStatus.OK)
            self.send_header('Content-Type', 'application/javascript; charset=utf-8')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        except OSError:
            self.send_error(HTTPStatus.NOT_FOUND, 'viewer.js not found')

    def _serve_index(self):
        lang = pick_lang(self.path, self.headers)
        T = TRANSLATIONS.get(lang, TRANSLATIONS['en'])
        try:
            images = sorted(
                [f for f in os.listdir(self.upload_dir) if is_allowed_image(f)],
                key=lambda x: os.path.getmtime(os.path.join(self.upload_dir, x)),
                reverse=True,
            )
        except FileNotFoundError:
            images = []
        rows = []
        for img in images:
            esc = html.escape(img)
            encoded = urllib.parse.quote(img)  # ensure safe URL
            rows.append(
                f'<div class="thumb"><a href="{encoded}" data-fn="{esc}" aria-label="Open {esc}">'  # accessibility label
                f'<img loading="lazy" src="{encoded}" alt="{esc}"></a><div class="cap" title="{esc}">{esc}</div></div>'
            )
        gallery = '\n'.join(rows) or f'<p class="empty">{html.escape(T["no_images"])}</p>'
        count = len(images)
        plural = '' if count == 1 else ('s' if lang == 'en' else 's')
        heading_suffix = T['heading_files_suffix'].format(count=count, plural=plural)
        notice = T['allowed_notice'].format(ext=', '.join(sorted(ALLOWED_EXT)), size=MAX_BYTES//1024//1024)
        footer = T['footer'].format(count=count, plural=plural)
        upload_label = html.escape(T['upload_btn'])
        choose_files_aria = html.escape(T['choose_files_aria'])
        upload_btn_aria = html.escape(T['upload_btn_aria'])
        page_title = html.escape(T['title'])
        switch_links = '<div style="font-size:.65rem;opacity:.65;margin-left:auto;">' \
                       f'<a href="?lang=en" style="color:#7aa7d6;text-decoration:none;">EN</a> | ' \
                       f'<a href="?lang=fr" style="color:#7aa7d6;text-decoration:none;">FR</a></div>'
        page = f"""<!DOCTYPE html>
<html lang="{lang}"><head><meta charset="UTF-8"><title>{page_title}</title>
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"/>
<style>
:root {{
  --bg:#0f141a; --panel:#16202a; --panel-border:#243240; --text:#dfe7f1; --accent:#1e5bbf; --accent-hover:#2b76f2;
  --danger:#d9534f; --focus:#2b76f2; --gap:12px;
}}
* {{ box-sizing:border-box; }}
body {{ font-family: system-ui,-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif; margin: clamp(.5rem, 2vw, 1.5rem); background:var(--bg); color:var(--text); -webkit-font-smoothing:antialiased; line-height:1.4; }}
h1 {{ margin:.25rem 0 1rem; font-size:clamp(1.4rem,4.5vw,1.8rem); letter-spacing:.5px; display:flex; gap:.75rem; align-items:baseline; flex-wrap:wrap; }}
form {{ background:var(--panel); padding:clamp(.75rem,2vw,1rem); border:1px solid var(--panel-border); border-radius:12px; margin-bottom:clamp(.9rem,2vw,1.2rem); display:flex; flex-wrap:wrap; gap:.75rem; align-items:center; }}
form input[type=file] {{ flex:1 1 240px; min-width:200px; }}
button {{ background:var(--accent); color:#fff; border:0; padding:.75rem 1.15rem; border-radius:8px; cursor:pointer; font-size:1rem; font-weight:600; letter-spacing:.25px; box-shadow:0 2px 4px #0006; transition:background .2s, transform .15s; }}
button:hover, button:focus-visible {{ background:var(--accent-hover); outline:none; }}
button:active {{ transform:translateY(2px); }}
button:focus-visible {{ box-shadow:0 0 0 3px #fff3,0 0 0 5px var(--focus); }}
.notice {{ flex:1 1 100%; font-size:.72rem; opacity:.7; }}
.gallery {{ display:flex; flex-wrap:wrap; gap:var(--gap); margin:0; padding:0; }}
.thumb {{ width:calc(25% - var(--gap)); max-width:200px; min-width:140px; flex:1 1 160px; text-align:center; font-size:.7rem; display:flex; flex-direction:column; gap:.35rem; }}
.thumb a {{ text-decoration:none; outline:none; border-radius:6px; }}
.thumb a:focus-visible img {{ box-shadow:0 0 0 3px #fff4,0 0 0 5px var(--focus); }}
.thumb img {{ width:100%; height:120px; object-fit:contain; background:#0b0f17; border:1px solid var(--panel-border); border-radius:8px; padding:6px; }}
.cap {{ overflow:hidden; text-overflow:ellipsis; white-space:nowrap; padding:0 .25rem; opacity:.85; }}
.empty {{ opacity:.6; font-style:italic; }}
footer {{ margin-top:2rem; font-size:.65rem; opacity:.55; text-align:center; }}
@media (max-width:900px) {{ .thumb {{ flex:1 1 calc(33.333% - var(--gap)); }} }}
@media (max-width:640px) {{ .thumb {{ flex:1 1 calc(50% - var(--gap)); }} form {{ flex-direction:column; align-items:stretch; }} form input[type=file] {{ width:100%; }} button {{ width:100%; }} .notice {{ font-size:.65rem; }} }}
@media (max-width:400px) {{ .thumb {{ flex:1 1 100%; }} body {{ margin:.75rem .5rem 2rem; }} }}
@media (prefers-reduced-motion:reduce) {{ * {{ animation:none!important; transition:none!important; }} }}
</style></head>
<body>
<h1>{page_title} <small style=\"font-size:.55em;font-weight:400;opacity:.6\">{heading_suffix}</small>{switch_links}</h1>
<form method=\"POST\" enctype=\"multipart/form-data\" aria-label=\"Upload images form\">
  <input type=\"file\" name=\"files\" multiple accept=\"image/*\" required aria-label=\"{choose_files_aria}\">
  <button type=\"submit\" aria-label=\"{upload_btn_aria}\">{upload_label}</button>
  <div class=\"notice\">{notice}</div>
</form>
<div class=\"gallery\">
{gallery}
</div>
<footer>{footer}</footer>
<script defer src="/viewer.js"></script>
</body></html>"""
        data = page.encode('utf-8')
        self.send_response(HTTPStatus.OK)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):  # noqa: N802
        ctype = self.headers.get('Content-Type', '')
        length = int(self.headers.get('Content-Length', '0') or 0)
        if length > MAX_BYTES:
            self.send_error(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, "Request too large")
            return
        if 'multipart/form-data' not in ctype.lower() or 'boundary=' not in ctype:
            self.send_error(HTTPStatus.BAD_REQUEST, 'Expected multipart/form-data')
            return
        boundary = None
        for part in ctype.split(';'):
            part = part.strip()
            if part.lower().startswith('boundary='):
                boundary = part.split('=', 1)[1]
                if boundary.startswith('"') and boundary.endswith('"'):
                    boundary = boundary[1:-1]
                break
        if not boundary:
            self.send_error(HTTPStatus.BAD_REQUEST, 'Boundary not found')
            return
        body = self.rfile.read(length)
        parts = parse_multipart(body, boundary)
        os.makedirs(self.upload_dir, exist_ok=True)
        saved = []
        for headers, content in parts:
            disp = headers.get('content-disposition')
            if not disp:
                continue
            field, filename = parse_content_disposition(disp)
            if field != 'files' or not filename:
                continue
            kind = detect_image(content)
            if kind is None:
                continue  # skip non-image
            fname = safe_filename(filename)
            # Ensure extension matches detected kind (basic normalization)
            ext = fname.rsplit('.', 1)[-1].lower() if '.' in fname else ''
            if kind and ext not in (kind, 'jpg' if kind == 'jpeg' else kind):
                fname = posixpath.splitext(fname)[0] + '.' + kind
            target = os.path.join(self.upload_dir, fname)
            base, ext_full = posixpath.splitext(fname)
            counter = 1
            while os.path.exists(target):
                target = os.path.join(self.upload_dir, f"{base}_{counter}{ext_full}")
                counter += 1
            try:
                with open(target, 'wb') as f:
                    f.write(content)
                saved.append(os.path.basename(target))
            except OSError:
                continue
        self.send_response(HTTPStatus.SEE_OTHER)
        self.send_header('Location', '/')
        self.send_header('Content-Type', 'text/plain; charset=utf-8')
        self.end_headers()
        self.wfile.write(f"Saved {len(saved)} file(s)".encode('utf-8'))

    def log_message(self, format: str, *args):  # noqa: A003
        sys.stderr.write("[%s] %s\n" % (time.strftime('%H:%M:%S'), format % args))

# -------------- CLI Runner --------------

def parse_args():
    p = argparse.ArgumentParser(description="Simple local image upload server")
    p.add_argument('--port', '-p', type=int, default=int(os.getenv('UPLOAD_PORT', '8080')),
                   help='Port to bind (default 8080)')
    p.add_argument('--dir', '-d', '--folder', dest='dir', default=os.getenv('UPLOAD_DIR', 'uploaded_images'),
                   help='Upload directory (default ./uploaded_images)')
    return p.parse_args()

def run():
    args = parse_args()
    os.makedirs(args.dir, exist_ok=True)
    def handler_factory(*h_args, **h_kwargs):
        return UploadHandler(*h_args, upload_dir=os.path.abspath(args.dir), **h_kwargs)
    server = ThreadingHTTPServer(('0.0.0.0', args.port), handler_factory)
    print(f"Upload server running on http://localhost:{args.port} -> {os.path.abspath(args.dir)}")
    print("Press Ctrl+C to stop.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping...")
    finally:
        server.server_close()

if __name__ == '__main__':
    run()
