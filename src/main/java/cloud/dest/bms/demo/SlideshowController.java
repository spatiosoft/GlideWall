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

    @FXML
    private void initialize() {
        intervalSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 3600, 5));
        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "slideshow-worker");
            t.setDaemon(true);
            return t;
        });
        // Bind image view size to root pane for responsive layout.
        imageView.fitWidthProperty().bind(rootPane.widthProperty());
        imageView.fitHeightProperty().bind(rootPane.heightProperty());
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setCache(true);
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
            rootDirectory = selected;
            status("Selected: " + rootDirectory);
            rebuildFileList();
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
                statusLabel.setText(String.format("Showing %s (%d images)", file.getFileName(), imageFiles.size()));
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

    private synchronized Path pickRandom() { // retained but unused now
        if (imageFiles.isEmpty()) return null;
        if (imageFiles.size() == 1) return imageFiles.get(0);
        Path chosen;
        int attempts = 0;
        do {
            chosen = imageFiles.get(random.nextInt(imageFiles.size()));
            attempts++;
        } while (chosen.equals(lastShown) && attempts < 5);
        lastShown = chosen;
        return chosen;
    }

    private void rebuildFileList() {
        if (rootDirectory == null) return;
        try (Stream<Path> stream = Files.walk(rootDirectory)) {
            List<Path> all = stream.filter(Files::isRegularFile).filter(this::isImageFile)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            if (shuffledMode) {
                Collections.shuffle(all, random);
            } else {
                Collections.sort(all); // ensure sorted order when not shuffled
            }
            synchronized (this) {
                imageFiles.clear();
                imageFiles.addAll(all);
                // Adjust currentIndex to still point at same file if possible
                if (lastShown != null) {
                    int idx = imageFiles.indexOf(lastShown);
                    currentIndex = idx >= 0 ? idx : -1;
                } else {
                    currentIndex = -1;
                }
            }
            Platform.runLater(() -> {
                observableImages.setAll(all);
                // prune cache entries no longer present
                thumbCache.keySet().removeIf(p -> !imageFiles.contains(p));
            });
        } catch (IOException ignored) { }
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

    private void cancelTask(ScheduledFuture<?> task) {
        if (task != null) task.cancel(false);
    }

    @FXML
    private void onToggleFullscreen() {
        if (imageView.getScene() == null) return;
        Stage stage = (Stage) imageView.getScene().getWindow();
        boolean newState = !stage.isFullScreen();
        if (newState) {
            // Apply UI changes immediately so toolbar hides even before property listener fires
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
            if (rootPane != null) rootPane.setStyle("-fx-background-color: black;");
        });
    }

    public void shutdown() {
        onStop();
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @FXML private void onManualRefresh() { // manual refresh button
        rebuildFileList();
        status("Refreshed");
    }
}
