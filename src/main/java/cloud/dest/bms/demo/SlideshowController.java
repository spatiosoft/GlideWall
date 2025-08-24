package cloud.dest.bms.demo;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*; // consolidate controls
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import javafx.stage.Stage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * Controller that manages a random slideshow of images found in a directory tree.
 * New images added to any subfolder are picked up automatically via periodic rescans.
 */
public class SlideshowController {

    @FXML private ImageView imageView;
    @FXML private Spinner<Integer> intervalSpinner;
    @FXML private Label statusLabel;
    @FXML private Button fullScreenButton;
    @FXML private BorderPane rootPane;
    @FXML private ToolBar toolBar;
    @FXML private ListView<Path> thumbList; // new list view
    @FXML private javafx.scene.layout.HBox statusBar; // new status bar
    @FXML private Label fileCountLabel;               // shows number of files
    @FXML private StackPane centerPane; // new center pane reference

    private Path rootDirectory;
    private final List<Path> imageFiles = new ArrayList<>();
    private final Random random = new Random();
    private final Map<Path, Image> thumbCache = new ConcurrentHashMap<>(); // cache thumbnails

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> slideshowTask;
    private ScheduledFuture<?> rescanTask;

    private volatile boolean running = false;
    private volatile Path lastShown;
    private boolean stageFsListenerInstalled = false; // added

    // Sequential slideshow state
    private int currentIndex = -1; // index of last shown image
    private boolean shuffledMode = false; // whether list is currently shuffled

    // Observable list for UI binding
    private final javafx.collections.ObservableList<Path> observableImages = javafx.collections.FXCollections.observableArrayList();
    private boolean suppressSelectionHandler = false; // new flag to avoid double-show

    private WatchService watchService;           // file system watcher
    private Future<?> watchTask;                 // task handling watch events

    @FXML
    private void initialize() {
        intervalSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 3600, 5));
        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "slideshow-worker");
            t.setDaemon(true);
            return t;
        });
        // Width can bind directly; height we manage manually to keep status bar visible.
        imageView.fitWidthProperty().bind(centerPane.widthProperty());
        // Remove any prior fitHeight binding just in case (defensive)
        imageView.fitHeightProperty().unbind();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setCache(true);
        // Allow full shrink
        rootPane.setMinHeight(0);
        centerPane.setMinHeight(0);
        if (thumbList != null) thumbList.setMinHeight(0);
        // Recompute available height whenever layout-related sizes change.
        Runnable sizeUpdater = this::updateAvailableImageHeight;
        rootPane.heightProperty().addListener((o, a, b) -> sizeUpdater.run());
        if (toolBar != null) toolBar.heightProperty().addListener((o,a,b)-> sizeUpdater.run());
        if (statusBar != null) statusBar.heightProperty().addListener((o,a,b)-> sizeUpdater.run());
        // Also run once after first layout pulse.
        Platform.runLater(sizeUpdater);
        // Scene listener for ESC and fullscreen property changes.
        imageView.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                // If window already exists
                if (newScene.getWindow() instanceof Stage st) {
                    installFullScreenListener(st);
                }
                // Also listen for window becoming available later
                newScene.windowProperty().addListener((o, oldWin, newWin) -> {
                    if (newWin instanceof Stage st2) {
                        installFullScreenListener(st2);
                    }
                });
                newScene.setOnKeyPressed(e -> {
                    if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                        if (newScene.getWindow() instanceof Stage st && st.isFullScreen()) {
                            st.setFullScreen(false);
                            e.consume();
                        }
                    }
                });
            }
        });
        setupThumbList();
    }

    private void installFullScreenListener(Stage stage) { // added helper
        if (stageFsListenerInstalled) return;
        stage.fullScreenProperty().addListener((o, was, isNow) -> applyFullscreenUI(isNow));
        stageFsListenerInstalled = true;
    }

    private void setupThumbList() {
        if (thumbList == null) return;
        thumbList.setItems(observableImages);
        thumbList.setCellFactory(list -> new ListCell<>() {
            private final ImageView iv = new ImageView();
            { iv.setFitWidth(100); iv.setFitHeight(75); iv.setPreserveRatio(true); }
            @Override protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null); setText(null);
                } else {
                    Image thumb = thumbCache.computeIfAbsent(item, p -> new Image(p.toUri().toString(), 120, 90, true, true, true));
                    iv.setImage(thumb);
                    setGraphic(iv);
                    setText(item.getFileName().toString());
                }
            }
        });
        thumbList.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null && !suppressSelectionHandler) {
                // User-initiated selection: show immediately (single display)
                showImage(sel);
            }
        });
    }

    @FXML
    private void onChooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Image Folder");
        Window w = imageView.getScene() != null ? imageView.getScene().getWindow() : null;
        Path selected = null;
        try {
            var dir = chooser.showDialog(w);
            if (dir != null) {
                selected = dir.toPath();
            }
        } catch (Exception ignored) {
        }
        if (selected != null) {
            // Stop any previous watcher before switching root
            stopWatcher();
            rootDirectory = selected;
            status("Selected: " + rootDirectory);
            rebuildFileList();
            startWatcher(); // start watching for live changes
        }
    }

    @FXML
    private void onStart() {
        if (running) return;
        if (rootDirectory == null) {
            status("Choose a folder first");
            return;
        }
        running = true;
        scheduleRescan();
        scheduleSlideshow();
        status("Running");
    }

    @FXML
    private void onStop() {
        if (!running) return;
        running = false;
        cancelTask(slideshowTask);
        cancelTask(rescanTask);
        status("Stopped");
    }

    private void scheduleSlideshow() {
        cancelTask(slideshowTask);
        int interval = intervalSpinner.getValue();
        slideshowTask = scheduler.scheduleAtFixedRate(this::showNextImage, 0, interval, TimeUnit.SECONDS);
        // Reschedule automatically if interval spinner value changes while running.
        intervalSpinner.valueProperty().addListener((obs, o, n) -> {
            if (running && n != null && !n.equals(o)) {
                scheduleSlideshow();
            }
        });
    }

    private void scheduleRescan() { // restored method
        cancelTask(rescanTask);
        rescanTask = scheduler.scheduleAtFixedRate(this::rebuildFileList, 0, 10, TimeUnit.SECONDS);
    }

    // Show next image sequentially, looping back to start.
    private void showNextImage() {
        if (!running) return;
        Path file;
        synchronized (this) {
            if (imageFiles.isEmpty()) return;
            if (currentIndex < 0 || currentIndex >= imageFiles.size()) {
                currentIndex = -1; // reset if out of bounds (e.g., list changed)
            }
            currentIndex = (currentIndex + 1) % imageFiles.size();
            file = imageFiles.get(currentIndex);
            lastShown = file;
        }
        showImage(file);
    }

    private void showImage(Path file) {
        try {
            Image img = new Image(file.toUri().toString(), true);
            Platform.runLater(() -> {
                imageView.setImage(img);
                imageView.setPreserveRatio(true);
                // Restore per-image status text in status bar
                if (statusLabel != null) {
                    statusLabel.setText(String.format("Showing %s (%d images)", file.getFileName(), imageFiles.size()));
                }
                if (thumbList != null && !Objects.equals(thumbList.getSelectionModel().getSelectedItem(), file)) {
                    suppressSelectionHandler = true;
                    thumbList.getSelectionModel().select(file);
                    thumbList.scrollTo(file);
                    suppressSelectionHandler = false;
                }
                synchronized (this) {
                    currentIndex = imageFiles.indexOf(file);
                    lastShown = file;
                }
            });
        } catch (Exception ignored) { }
    }

    private void rebuildFileList() {
        if (rootDirectory == null) return;
        // Snapshot old list for change detection & order preservation
        List<Path> oldList;
        synchronized (this) {
            oldList = new ArrayList<>(imageFiles);
        }
        Set<Path> oldSet = new HashSet<>(oldList);
        try (Stream<Path> stream = Files.walk(rootDirectory)) {
            List<Path> discovered = stream.filter(Files::isRegularFile).filter(this::isImageFile)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

            // Detect additions
            List<Path> added = new ArrayList<>();
            for (Path p : discovered) {
                if (!oldSet.contains(p)) added.add(p);
            }

            boolean anyAdded = !added.isEmpty();
            boolean anyRemoved = oldList.size() > discovered.size(); // simple heuristic

            List<Path> finalOrder;
            if (anyAdded) {
                // Reshuffle entire list when new images appear
                finalOrder = new ArrayList<>(discovered);
                Collections.shuffle(finalOrder, random);
                shuffledMode = true; // enter shuffled mode automatically
            } else if (shuffledMode) {
                // Preserve existing shuffled order; just drop removed items
                finalOrder = new ArrayList<>();
                for (Path p : oldList) {
                    if (discovered.contains(p)) finalOrder.add(p);
                }
                // No additions so no new items need insertion
            } else {
                // Not in shuffle mode and no additions: keep sorted alphabetical
                finalOrder = new ArrayList<>(discovered);
                Collections.sort(finalOrder);
            }

            synchronized (this) {
                imageFiles.clear();
                imageFiles.addAll(finalOrder);
                if (lastShown != null) {
                    int idx = imageFiles.indexOf(lastShown);
                    currentIndex = idx >= 0 ? idx : -1;
                } else {
                    currentIndex = -1;
                }
            }
            Platform.runLater(() -> {
                observableImages.setAll(finalOrder);
                thumbCache.keySet().removeIf(p -> !imageFiles.contains(p));
                if (fileCountLabel != null) fileCountLabel.setText(String.valueOf(finalOrder.size()));
                if (anyAdded) {
                    status(String.format("New images: %d (auto-reshuffled)", added.size()));
                } else if (anyRemoved) {
                    status("Images removed (list updated)");
                }
            });
        } catch (IOException ignored) { }
    }

    // Start a recursive WatchService that triggers rebuild on changes.
    private void startWatcher() {
        if (rootDirectory == null) return;
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerAll(rootDirectory);
            watchTask = scheduler.submit(this::processWatchEvents);
        } catch (IOException e) {
            status("Watcher error: " + e.getMessage());
        }
    }

    private void registerAll(Path start) throws IOException {
        try (Stream<Path> dirs = Files.walk(start)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                try {
                    dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                } catch (IOException ignored) { }
            });
        }
    }

    private void processWatchEvents() {
        while (watchService != null && !Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException cwse) {
                break;
            }
            Path dir = (Path) key.watchable();
            boolean refreshNeeded = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) continue;
                Path name = (Path) event.context();
                Path child = dir.resolve(name);
                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    if (Files.isDirectory(child)) {
                        try { registerAll(child); } catch (IOException ignored) { }
                        refreshNeeded = true; // new folder may contain images
                    } else if (isImageFile(child)) {
                        refreshNeeded = true;
                    }
                } else if (kind == StandardWatchEventKinds.ENTRY_DELETE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    if (Files.isDirectory(child) || isImageFile(child)) {
                        refreshNeeded = true;
                    }
                }
            }
            boolean valid = key.reset();
            if (refreshNeeded) {
                rebuildFileList();
            }
            if (!valid) {
                // Directory no longer accessible; trigger full rebuild to clean stale entries.
                rebuildFileList();
            }
        }
    }

    private void stopWatcher() {
        if (watchTask != null) {
            watchTask.cancel(true);
            watchTask = null;
        }
        if (watchService != null) {
            try { watchService.close(); } catch (IOException ignored) { }
            watchService = null;
        }
    }

    // Shuffle button handler
    @FXML
    private void onShuffle() {
        synchronized (this) {
            if (imageFiles.isEmpty()) return;
            Collections.shuffle(imageFiles, random);
            shuffledMode = true;
            // Recalculate currentIndex relative to lastShown
            if (lastShown != null) {
                currentIndex = imageFiles.indexOf(lastShown);
            } else {
                currentIndex = -1; // next tick will start at 0
            }
        }
        Platform.runLater(() -> {
            observableImages.setAll(new ArrayList<>(imageFiles));
            status("Shuffled");
        });
    }

    private boolean isImageFile(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".webp");
    }

    private void status(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg));
    }

    private void cancelTask(java.util.concurrent.ScheduledFuture<?> task) {
        if (task != null) task.cancel(false);
    }

    @FXML
    private void onToggleFullscreen() {
        if (imageView.getScene() == null) return;
        Stage stage = (Stage) imageView.getScene().getWindow();
        boolean newState = !stage.isFullScreen();
        if (newState) {
            applyFullscreenUI(true);
            stage.setFullScreenExitHint("");
        } else {
            applyFullscreenUI(false);
        }
        stage.setFullScreen(newState);
    }

    private void applyFullscreenUI(boolean full) {
        Platform.runLater(() -> {
            if (fullScreenButton != null) fullScreenButton.setText(full ? "Windowed" : "Fullscreen");
            if (toolBar != null) { toolBar.setVisible(!full); toolBar.setManaged(!full); }
            if (thumbList != null) { thumbList.setVisible(!full); thumbList.setManaged(!full); }
            if (statusBar != null) { statusBar.setVisible(!full); statusBar.setManaged(!full); }
            if (rootPane != null) rootPane.setStyle("-fx-background-color: black;");
            updateAvailableImageHeight(); // recompute after visibility changes
        });
    }

    public void shutdown() {
        onStop();
        stopWatcher();
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @FXML private void onManualRefresh() {
        rebuildFileList();
        status("Refreshed");
    }

    private void updateAvailableImageHeight() {
        if (rootPane == null || imageView == null) return;
        double total = rootPane.getHeight();
        double top = (toolBar != null && toolBar.isVisible()) ? toolBar.getHeight() : 0;
        double bottom = (statusBar != null && statusBar.isVisible()) ? statusBar.getHeight() : 0;
        // Account for padding (approx) if any; could read from insets but simple constant ok.
        double padding = 10; // top+bottom combined approx
        double available = total - top - bottom - padding;
        if (available < 0) available = 0;
        imageView.setFitHeight(available);
    }
}
