# GlideWall

GlideWall is a JavaFX desktop application that plays an auto‑updating slideshow of every image inside a chosen folder and all its subfolders. It continuously watches the directory tree: newly added images appear automatically; removed images disappear. Ideal for a passive photo wall / digital signage / inspiration board.

## Key Features
- Recursive folder scan (initial + periodic refresh + file system watch)
- Automatic detection of added / removed images (WatchService + scheduled rescan)
- Adjustable interval (1–3600 seconds) via spinner
- Start / Stop controls
- Sequential looping with stable ordering; optional Shuffle (and auto‑reshuffle when new images arrive)
- Thumbnail side panel (click any thumbnail to jump immediately)
- Fullscreen toggle (ESC exits; UI chrome and lists hide in fullscreen for a clean wall display)
- Status bar with current image name and total file count
- Manual Refresh button (force immediate rescan)
- Placeholder guidance when no folder or images are available
- Efficient thumbnail caching
- Optional local “Server Mode” (Python web uploader + mobile gallery + QR code)

Supported image formats (by simple extension match): JPG, JPEG, PNG, GIF, BMP, WEBP.

## Quick Start
Prerequisites:
- Java 21 (matching the pom.xml <source>/<target>)
- Maven (wrapper included) and JavaFX runtime fetched via dependencies

Run:
```
./mvnw clean javafx:run
```
(Windows: `mvnw.cmd clean javafx:run`)

Then:
1. Click "Choose Folder" and select a directory containing images (subfolders included).
2. (Optional) Adjust the interval spinner.
3. Press Start. Use Shuffle any time.
4. Add or remove images in the folder tree; changes appear automatically.
5. Toggle Fullscreen for display mode (ESC to exit fullscreen).

## Server Mode (Optional Local Web Uploader & Mobile Viewer)
GlideWall can launch a lightweight Python HTTP server so you (or anyone on your LAN) can upload images directly from a browser or mobile device into the currently selected slideshow folder.

### What It Provides
- Drag/drop or multi‑select upload form (accepts JPG/JPEG/PNG/GIF/BMP/WEBP)
- Instant appearance of newly uploaded images in the running slideshow (via watcher + periodic rescan)
- Auto‑listed thumbnails sorted by most recent
- Built‑in fullscreen viewer (click a thumbnail: overlay + arrow / swipe navigation + ESC close)
- Mobile friendly layout + swipe left/right gestures
- QR code in the JavaFX Server window for quick phone access
- Automatic local network URL selection (attempts a site‑local IPv4; falls back to localhost)

### Requirements
- Python 3 available on PATH (`python3` on Unix, `python` on Windows)
- The script `uploader/upload_server.py` (bundled in repo)

### Usage Steps
1. In GlideWall pick a folder (required before starting server).
2. Press the "Server" button, then click "Start" in the server window.
3. Note the displayed URL or scan the QR code with a mobile device on the same network.
4. Use the web page to upload images – they appear in the slideshow automatically.
5. Click any web thumbnail to open fullscreen; swipe or use arrow keys to navigate.

### Security / Limitations
- Intended for trusted local networks only: NO authentication, encryption, rate limiting, or sandboxing.
- Fixed port 8080 (current implementation). If the port is in use, the server will fail to start.
- All uploaded files are written directly into the chosen folder tree (basic filename sanitization + signature sniffing, but no deep image validation).
- No HTTPS / TLS; use behind a secure LAN or tunnel if needed.
- Large uploads limited by a simple size cap (25MB per request).

### Stopping
Use the "Stop" button in the Server window or close the GlideWall app (which stops the process).

## How It Works (Overview)
- A recursive scan builds an in‑memory list of image Paths.
- A WatchService registers every subdirectory to detect create / delete / modify events.
- A scheduled task (every 10s) performs a lightweight rebuild as a safety net.
- The slideshow scheduler advances at fixed rate (interval spinner value) on a background thread.
- UI updates are marshalled onto the JavaFX Application Thread (Platform.runLater).
- Thumbnails are generated on demand and cached.
- When new images are detected the list is reshuffled (if shuffle mode) to integrate them fairly.
- Server Mode (if active) writes files into the folder, triggering the same detection pipeline.

## Project Structure
- `HelloApplication` boots the JavaFX app, loads FXML + CSS, sets window title/icon.
- `slideshow-view.fxml` defines layout (toolbar, thumbnail list, main image view, status bar, placeholder label).
- `SlideshowController` encapsulates logic (scanning, watching, scheduling, UI state, fullscreen handling, shuffle, manual refresh, about dialog, server mode launcher).
- `uploader/upload_server.py` optional Python HTTP uploader + gallery.
- `uploader/viewer.js` client-side fullscreen + swipe gallery script.
- `slideshow.css` styles the UI.

## Build / Packaging
Create a runnable app image (via the JavaFX Maven plugin & jlink configuration) after adjusting plugin settings if desired:
```
./mvnw clean package
```
(See `javafx-maven-plugin` config in `pom.xml` for jlink image parameters.)

### Standalone Fat JAR
A shaded ("fat") JAR is produced using the Maven Shade Plugin.

Build (skip tests if desired):
```
./mvnw -DskipTests package
```
Resulting JAR:
```
target/glidewall-all.jar
```
Run:
```
java -jar target/glidewall-all.jar
```
Notes:
- The module descriptor is excluded so the app runs in classic classpath mode.
- If you encounter JavaFX native library issues on some platforms, prefer the jlink image below.

### Custom Runtime Image (jlink)
```
./mvnw clean javafx:jlink
```
Run the generated image:
```
./target/app/bin/app
```
This bundles only the required modules + JavaFX natives for the build platform.

## Configuration Ideas (Not Yet Implemented)
- Persist last used folder & interval
- Multi‑monitor fullscreen support
- Transition effects & fade durations
- Filter by extension / size / date
- Exclude specific subfolders
- Server mode: configurable port / authentication / HTTPS

## Contributing
Issues and pull requests are welcome. Please discuss substantial changes first so the scope aligns with GlideWall's lightweight goals.

## License
GlideWall is released under the GNU Affero General Public License version 3 (AGPLv3). This strong copyleft license ensures that if you modify and operate GlideWall over a network (e.g., embed in a server or remote display management tool), you must make your modified source available to users who interact with it.

SPDX-License-Identifier: AGPL-3.0-only

For full terms see: https://www.gnu.org/licenses/agpl-3.0.html

(Consider adding a dedicated `LICENSE` file with the complete AGPLv3 text if distributing.)

## Attribution
Developed using JavaFX and Maven, with assistance from AI tooling for documentation and refactoring.
