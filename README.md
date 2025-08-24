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

## How It Works (Overview)
- A recursive scan builds an in‑memory list of image Paths.
- A WatchService registers every subdirectory to detect create / delete / modify events.
- A scheduled task (every 10s) performs a lightweight rebuild as a safety net.
- The slideshow scheduler advances at fixed rate (interval spinner value) on a background thread.
- UI updates are marshalled onto the JavaFX Application Thread (Platform.runLater).
- Thumbnails are generated on demand and cached.
- When new images are detected the list is reshuffled (if shuffle mode) to integrate them fairly.

## Project Structure
- `HelloApplication` boots the JavaFX app, loads FXML + CSS, sets window title/icon.
- `slideshow-view.fxml` defines layout (toolbar, thumbnail list, main image view, status bar, placeholder label).
- `SlideshowController` encapsulates logic (scanning, watching, scheduling, UI state, fullscreen handling, shuffle, manual refresh, about dialog).
- `slideshow.css` styles the UI.

## Build / Packaging
Create a runnable app image (via the JavaFX Maven plugin & jlink configuration) after adjusting plugin settings if desired:
```
./mvnw clean package
```
(See `javafx-maven-plugin` config in `pom.xml` for jlink image parameters.)

## Configuration Ideas (Not Yet Implemented)
- Persist last used folder & interval
- Multi‑monitor fullscreen support
- Transition effects & fade durations
- Filter by extension / size / date
- Exclude specific subfolders

## Contributing
Issues and pull requests are welcome. Please discuss substantial changes first so the scope aligns with GlideWall's lightweight goals.

## License
GlideWall is released under the GNU Affero General Public License version 3 (AGPLv3). This strong copyleft license ensures that if you modify and operate GlideWall over a network (e.g., embed in a server or remote display management tool), you must make your modified source available to users who interact with it.

SPDX-License-Identifier: AGPL-3.0-only

For full terms see: https://www.gnu.org/licenses/agpl-3.0.html

(Consider adding a dedicated `LICENSE` file with the complete AGPLv3 text if distributing.)

## Attribution
Developed using JavaFX and Maven, with assistance from AI tooling for documentation and refactoring.

