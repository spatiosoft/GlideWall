# About GlideWall

[Home](index.md) · [Help](help.md) · [About](about.md)

GlideWall is a lightweight JavaFX desktop application for continuously presenting a folder tree of images as a dynamic slideshow. It targets scenarios like digital photo walls, informal office signage, and creative mood boards where frictionless updates matter more than heavy media management features.

![GlideWall Logo](assets/glidewall-logo.svg)

## Motivation
Most slideshow tools either:
- Require manual import or re-building playlists, or
- Miss newly added files unless restarted, or
- Are heavyweight (DAM / gallery servers) for a simple rotating wall.

GlideWall instead watches the actual filesystem so you can just drop images into nested folders and watch them appear automatically.

## Design Highlights
- Java 21 + JavaFX for a native-feel cross‑platform UI
- NIO WatchService (recursive registration) for near real-time change detection
- Scheduled periodic rescan (safety net against missed events / network shares)
- Background thread pool (daemon) for scanning + scheduling; UI updates marshalled through Platform.runLater
- Thumbnail list bound to an observable collection; cache for small preview Images
- Shuffle state logic that preserves fair integration of late-arriving images
- Fullscreen mode that hides chrome & list for clean display

## Architecture (Conceptual)
```
+-------------------+         +---------------------+
|  UI (JavaFX)      | <-----> |  SlideshowController |
|  - ImageView      |         |  - State (images)   |
|  - Thumbs List    |         |  - Scheduler        |
|  - Toolbar/Status |         |  - Watcher + Rescan |
+---------+---------+         +----------+----------+
          ^                               |
          | image / status updates        | filesystem events + periodic tasks
          |                               v
          |                      +------------------+
          |                      |  File System     |
          |                      | (Recursive Tree) |
          +----------------------+------------------+
```

## Ordering & Shuffle Strategy
1. Initial selection: alphabetical order (deterministic).
2. Shuffle invoked: list randomized; mode flagged.
3. New images while shuffled: entire set reshuffled (fair insertion) and position of current image preserved when possible.
4. Removals: list pruned without losing current index context.

## Performance Considerations
- Thumbnails created at modest size; disk paths used as cache keys.
- Image display uses on-demand loading (JavaFX Image with background loading flag).
- Rebuild operations snapshot existing order to minimize disruptive reordering when not required.

## Server Mode (Optional Uploader & Web Viewer)
GlideWall can optionally launch a lightweight Python HTTP server tied to the currently selected folder. This enables direct uploads from any device on the same local network and provides a mobile‑friendly thumbnail gallery with a built‑in fullscreen + swipe viewer.

### Highlights
- Drag/drop multi‑file upload (JPG/JPEG/PNG/GIF/BMP/WEBP)
- Immediate integration with the running slideshow (filesystem watcher + periodic rescan)
- Thumbnails ordered newest first
- Javascript fullscreen overlay: arrow keys & swipe navigation, ESC / × to exit
- QR code shown in the desktop Server window for quick mobile access
- Attempts to advertise a site‑local IPv4; falls back to localhost

### Usage Summary
1. Select a slideshow folder.
2. Open Server window (Server button) and click Start.
3. Scan QR code or open displayed URL (default http://<local-ip>:8080/).
4. Upload images; they appear in the slideshow automatically.
5. Click any web thumbnail for fullscreen navigation.

### Security & Limits
| Aspect | Current Behavior |
|--------|------------------|
| Auth | None (trusted LAN only) |
| Transport | HTTP (no TLS) |
| Port | Fixed 8080 |
| Validation | Basic filename sanitization + simple signature sniff |
| Size limit | 25MB per request |
| Rate limiting | None |

Do not expose directly to untrusted networks without adding authentication, HTTPS (reverse proxy), and stricter validation.

### Future Enhancements (Potential)
- Configurable port & bind address
- Optional authentication / access token
- HTTPS support (self‑signed or reverse proxy guidance)
- Upload quotas / rate limiting

## Limitations (Current)
- No fade / dissolve transitions
- No persistence of last folder or interval between sessions
- No include/exclude pattern filtering
- Single-window / single-monitor focus

## Future Ideas
- Smooth transitions (cross-fade, Ken Burns)
- Persisted preferences (folder, interval, shuffle state)
- Glob / regex filtering and exclusion lists
- Multi-monitor spanning or per-monitor independent loops
- EXIF / metadata overlay (date taken, caption)

## License
GlideWall is released under the GNU Affero General Public License v3.0 (AGPL-3.0-only). This ensures that improvements deployed for network access are also shared back with users.

SPDX-License-Identifier: AGPL-3.0-only

## Attribution
Developed with JavaFX and Maven; documentation generated with assistance from AI tooling.

[Home](index.md) · [Help](help.md) · [About](about.md)
