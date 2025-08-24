![GlideWall Logo](assets/glidewall-logo.svg)

# GlideWall

[Home](index.md) · [Help](help.md) · [About](about.md)

Adaptive, auto-updating desktop slideshow for a folder tree of images.

## What Is It?
GlideWall is a JavaFX application that continuously displays every image inside a chosen folder (recursing into subfolders). Add or remove files at any time: the slideshow reflects changes automatically.

## Core Features
- Recursive scan + filesystem watch + periodic rescan
- Auto-detect additions / deletions
- Adjustable interval (1–3600s)
- Start / Stop controls
- Shuffle mode (auto-reshuffles when new images appear)
- Thumbnail side panel (jump instantly to any image)
- Fullscreen toggle (ESC exits; chrome hidden)
- Status bar with image name + count
- Manual refresh
- Placeholder guidance when empty
- Optional local Server Mode (Python web uploader + mobile gallery + QR code)

Supported formats: JPG, JPEG, PNG, GIF, BMP, WEBP.

## Quick Start
1. Install Java 21 and Maven.
2. Clone the repo.
3. Run:
```bash
mvn clean javafx:run
```
4. Click "Choose Folder" and select your root image folder.
5. Press Start. Optionally adjust interval or press Shuffle.
6. Add more images; they appear automatically.

## Server Mode (Optional)
A lightweight Python HTTP server can be launched from the desktop app (Server button) to let you upload images from any device on your local network.

### Capabilities
- Multi-file / drag & drop upload form (JPG/JPEG/PNG/GIF/BMP/WEBP)
- Immediate slideshow integration (watcher + periodic rescan)
- Mobile-friendly thumbnail grid
- Built-in fullscreen viewer (click → overlay; swipe or arrow keys navigate)
- QR code shown in the JavaFX server window for fast phone access
- Automatic attempt to pick a site-local IPv4 (falls back to localhost)

### How To Use
1. In GlideWall choose a folder (required first).
2. Click "Server" → "Start".
3. Scan the QR code or open the displayed URL (default port 8080) from a device on the same LAN.
4. Upload images; they will appear in the running slideshow.
5. Use the web gallery thumbnails for fullscreen viewing and swipe navigation.

### Limits & Warnings
- Local / trusted networks only: no auth, no HTTPS/TLS, no rate limiting.
- Fixed port 8080 (current). If busy, start fails (check logs in window).
- Basic filename sanitization + simple image signature sniffing; not a hardened service.
- 25MB request size cap; oversized requests rejected.

Stop via the Server window "Stop" button or by closing GlideWall.

## Typical Uses
- Digital photo frame / wall
- Office / lobby signage
- Mood board / inspiration loop
- Monitoring still image outputs (dashboards exported as PNGs, etc.)

## Roadmap Ideas
- Persist last folder & settings
- Transition effects (fade, zoom)
- Multi-monitor support
- Include / exclude patterns
- Metadata overlays
- Server mode: configurable port, auth, HTTPS

## Contributing
Issues and PRs welcome. Keep scope lean; favor fast startup and low friction.

## License
AGPL-3.0-only. Strong copyleft, including network use. See LICENSE or online text.

SPDX-License-Identifier: AGPL-3.0-only

---
See [Help](help.md) for detailed usage and [About](about.md) for background.
