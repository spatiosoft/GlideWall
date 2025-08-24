# Help

Comprehensive usage guide for GlideWall.

## Installation
1. Install Java 21.
2. (Optional) Ensure Maven is installed or use the provided wrapper scripts.
3. Clone the repository.
4. Run:
```bash
mvn clean javafx:run
```

## UI Overview
| Area | Purpose |
|------|---------|
| Toolbar | Folder selection, playback controls, shuffle, fullscreen, manual refresh, About. |
| Thumbnail List (left) | Scrollable list of all discovered images. Click any to jump immediately. Hidden in fullscreen. |
| Center Image View | Displays the currently active image scaled to fit (preserves aspect). |
| Status Bar | Shows status messages, total image count, and current playback state. |
| Placeholder Label | Guidance when no folder is chosen or no images are found. |

## Workflow
1. Click "Choose Folder" to select a root directory. All subfolders are scanned.
2. Adjust the interval spinner (seconds between slides).
3. Press Start to begin autoplay.
4. Use Shuffle at any time to randomize the current list. New images trigger an auto-reshuffle if shuffle has been used.
5. Add or remove images on disk; they appear/disappear automatically (watch service + periodic rescan).
6. Click any thumbnail to display it immediately without interrupting the running slideshow schedule.
7. Toggle Fullscreen for a clean display (ESC exits). UI chrome and thumbnails hide automatically.

## File Watching Behavior
GlideWall combines three mechanisms:
- Initial recursive scan
- Java NIO WatchService (directory events) for near real-time additions/removals
- Periodic (10s) rescan as a safety net (covers missed events or nested changes)

## Ordering Logic
- Fresh scan after choosing a folder: alphabetical until you press Shuffle.
- Shuffle pressed: images are randomized; state marked as shuffled.
- New images appear while shuffled: entire list reshuffled to integrate fairly.
- Images removed: list pruned while preserving current shuffled ordering.

## Interval Changes
Changing the interval while running restarts the scheduled slideshow task with the new delay (no app restart needed).

## Supported Formats
JPG, JPEG, PNG, GIF, BMP, WEBP (matched by file extension, case-insensitive).

## Performance Tips
- Large folders: first scan may take time; progress appears via status messages.
- Thumbnails: Generated lazily and cached; clearing the cache occurs when files disappear.
- Network drives: Watch events may be slower; periodic rescan helps maintain accuracy.

## Troubleshooting
| Symptom | Suggestion |
|---------|------------|
| No images found | Verify folder actually contains supported formats (not just RAW). |
| New images slow to appear | Give up to 10s (rescan window) or use Manual Refresh. |
| High CPU on huge trees | Increase interval; reduce frequency of external file writes. |
| ESC not exiting fullscreen | Ensure window focus; press ESC once (exit hint disabled intentionally). |

## Keyboard & Interaction
| Action | Effect |
|--------|--------|
| ESC | Exit fullscreen mode |
| Click thumbnail | Jump to that image immediately |

## Manual Refresh
Forces immediate rebuild of the file list (useful after bulk operations or if a network share lags).

## Limitations
- No transition effects yet (instant swap)
- No persistence of settings across launches
- No filtering/exclusion patterns

## Planned Enhancements (Subject to Change)
- Fades / cross-dissolve transitions
- Remember last folder and interval
- Include / exclude glob patterns
- Multi-monitor spanning / per-display control
- Tag or metadata overlay (EXIF date, etc.)

## License
AGPL-3.0-only. See About page and root README for rationale.

Return to [Home](./index.md) · See [About](./about.md)

