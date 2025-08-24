![GlideWall Logo](assets/glidewall-logo.svg)

# GlideWall
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

## Contributing
Issues and PRs welcome. Keep scope lean; favor fast startup and low friction.

## License
AGPL-3.0-only. Strong copyleft, including network use. See LICENSE or online text.

SPDX-License-Identifier: AGPL-3.0-only

---
See Help for detailed usage and About for background.

