# About GlideWall

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

Return to [Home](./index.md) · See [Help](./help.md)

