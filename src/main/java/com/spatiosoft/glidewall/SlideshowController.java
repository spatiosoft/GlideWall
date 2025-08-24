package com.spatiosoft.glidewall;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import javafx.stage.Stage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

public class SlideshowController {
    @FXML private ImageView imageView;
    @FXML private Spinner<Integer> intervalSpinner;
    @FXML private Label statusLabel;
    @FXML private Button fullScreenButton;
    @FXML private BorderPane rootPane;
    @FXML private ToolBar toolBar;
    @FXML private ListView<Path> thumbList;
    @FXML private javafx.scene.layout.HBox statusBar;
    @FXML private Label fileCountLabel;
    @FXML private StackPane centerPane;
    @FXML private Label placeholderLabel;

    private Path rootDirectory;
    private final List<Path> imageFiles = new ArrayList<>();
    private final Random random = new Random();
    private final Map<Path, Image> thumbCache = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> slideshowTask;
    private ScheduledFuture<?> rescanTask;

    private volatile boolean running = false;
    private volatile Path lastShown;
    private boolean stageFsListenerInstalled = false;

    private int currentIndex = -1;
    private boolean shuffledMode = false;

    private final javafx.collections.ObservableList<Path> observableImages = javafx.collections.FXCollections.observableArrayList();
    private boolean suppressSelectionHandler = false;

    private WatchService watchService;
    private Future<?> watchTask;

    @FXML
    private void initialize() {
        intervalSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 3600, 5));
        scheduler = Executors.newScheduledThreadPool(2, r -> { Thread t = new Thread(r, "slideshow-worker"); t.setDaemon(true); return t; });
        imageView.fitWidthProperty().bind(centerPane.widthProperty());
        imageView.fitHeightProperty().unbind();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setCache(true);
        rootPane.setMinHeight(0); centerPane.setMinHeight(0); if (thumbList != null) thumbList.setMinHeight(0);
        Runnable sizeUpdater = this::updateAvailableImageHeight;
        rootPane.heightProperty().addListener((o,a,b)-> sizeUpdater.run());
        if (toolBar != null) toolBar.heightProperty().addListener((o,a,b)-> sizeUpdater.run());
        if (statusBar != null) statusBar.heightProperty().addListener((o,a,b)-> sizeUpdater.run());
        Platform.runLater(sizeUpdater);
        imageView.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                if (newScene.getWindow() instanceof Stage st) { installFullScreenListener(st); }
                newScene.windowProperty().addListener((o, oldWin, newWin) -> { if (newWin instanceof Stage st2) installFullScreenListener(st2); });
                newScene.setOnKeyPressed(e -> { if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) { if (newScene.getWindow() instanceof Stage st && st.isFullScreen()) { st.setFullScreen(false); e.consume(); } } });
            }
        });
        setupThumbList();
        Platform.runLater(this::updatePlaceholderVisibility);
    }

    private void installFullScreenListener(Stage stage) {
        if (stageFsListenerInstalled) return;
        stage.fullScreenProperty().addListener((o, was, isNow) -> applyFullscreenUI(isNow));
        stageFsListenerInstalled = true;
    }

    private void setupThumbList() {
        if (thumbList == null) return;
        thumbList.setItems(observableImages);
        thumbList.setCellFactory(list -> new ListCell<>() {
            private final ImageView iv = new ImageView(); { iv.setFitWidth(100); iv.setFitHeight(75); iv.setPreserveRatio(true); }
            @Override protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); }
                else { Image thumb = thumbCache.computeIfAbsent(item, p -> new Image(p.toUri().toString(), 120,90,true,true,true)); iv.setImage(thumb); setGraphic(iv); setText(item.getFileName().toString()); }
            }
        });
        thumbList.getSelectionModel().selectedItemProperty().addListener((obs,o,sel)-> { if (sel!=null && !suppressSelectionHandler) showImage(sel); });
    }

    @FXML private void onChooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser(); chooser.setTitle("Select Image Folder");
        Window w = imageView.getScene()!=null? imageView.getScene().getWindow(): null;
        Path selected = null; try { var dir = chooser.showDialog(w); if (dir!=null) selected = dir.toPath(); } catch (Exception ignored) {}
        if (selected!=null) { stopWatcher(); rootDirectory = selected; status("Selected: " + rootDirectory); rebuildFileList(); startWatcher(); }
    }

    @FXML private void onStart() { if (running) return; if (rootDirectory==null) { status("Choose a folder first"); return; } running=true; scheduleRescan(); scheduleSlideshow(); status("Running"); }
    @FXML private void onStop() { if (!running) { updatePlaceholderVisibility(); return; } running=false; cancelTask(slideshowTask); cancelTask(rescanTask); status("Stopped"); updatePlaceholderVisibility(); }

    private void scheduleSlideshow() { cancelTask(slideshowTask); int interval = intervalSpinner.getValue(); slideshowTask = scheduler.scheduleAtFixedRate(this::showNextImage,0,interval,TimeUnit.SECONDS); intervalSpinner.valueProperty().addListener((obs,o,n)-> { if (running && n!=null && !n.equals(o)) scheduleSlideshow(); }); }
    private void scheduleRescan() { cancelTask(rescanTask); rescanTask = scheduler.scheduleAtFixedRate(this::rebuildFileList,0,10,TimeUnit.SECONDS); }

    private void showNextImage() { if (!running) return; Path file; synchronized (this) { if (imageFiles.isEmpty()) return; if (currentIndex <0 || currentIndex>= imageFiles.size()) currentIndex = -1; currentIndex = (currentIndex+1) % imageFiles.size(); file = imageFiles.get(currentIndex); lastShown = file; } showImage(file); }

    private void showImage(Path file) { try { Image img = new Image(file.toUri().toString(), true); Platform.runLater(()-> { imageView.setImage(img); imageView.setPreserveRatio(true); if (statusLabel!=null) statusLabel.setText(String.format("Showing %s (%d images)", file.getFileName(), imageFiles.size())); if (thumbList!=null && !Objects.equals(thumbList.getSelectionModel().getSelectedItem(), file)) { suppressSelectionHandler=true; thumbList.getSelectionModel().select(file); thumbList.scrollTo(file); suppressSelectionHandler=false; } synchronized (this) { currentIndex = imageFiles.indexOf(file); lastShown = file; } updatePlaceholderVisibility(); }); } catch (Exception ignored) {} }

    private void rebuildFileList() { if (rootDirectory==null) { Platform.runLater(this::updatePlaceholderVisibility); return; } List<Path> oldList; synchronized (this) { oldList = new ArrayList<>(imageFiles); } Set<Path> oldSet = new HashSet<>(oldList); try (Stream<Path> stream = Files.walk(rootDirectory)) { List<Path> discovered = stream.filter(Files::isRegularFile).filter(this::isImageFile).collect(java.util.stream.Collectors.toCollection(ArrayList::new)); List<Path> added = new ArrayList<>(); for (Path p: discovered) if (!oldSet.contains(p)) added.add(p); boolean anyAdded = !added.isEmpty(); boolean anyRemoved = oldList.size() > discovered.size(); List<Path> finalOrder; if (anyAdded) { finalOrder = new ArrayList<>(discovered); Collections.shuffle(finalOrder, random); shuffledMode = true; } else if (shuffledMode) { finalOrder = new ArrayList<>(); for (Path p: oldList) if (discovered.contains(p)) finalOrder.add(p); } else { finalOrder = new ArrayList<>(discovered); Collections.sort(finalOrder); }
        synchronized (this) { imageFiles.clear(); imageFiles.addAll(finalOrder); if (lastShown!=null) { int idx = imageFiles.indexOf(lastShown); currentIndex = idx>=0? idx : -1; } else currentIndex = -1; }
        Platform.runLater(()-> { observableImages.setAll(finalOrder); thumbCache.keySet().removeIf(p-> !imageFiles.contains(p)); if (fileCountLabel!=null) fileCountLabel.setText(String.valueOf(finalOrder.size())); if (anyAdded) status(String.format("New images: %d (auto-reshuffled)", added.size())); else if (anyRemoved) status("Images removed (list updated)"); updatePlaceholderVisibility(); }); } catch (IOException ignored) { Platform.runLater(this::updatePlaceholderVisibility); } }

    private void startWatcher() { if (rootDirectory==null) return; try { watchService = FileSystems.getDefault().newWatchService(); registerAll(rootDirectory); watchTask = scheduler.submit(this::processWatchEvents); } catch (IOException e) { status("Watcher error: " + e.getMessage()); } }
    private void registerAll(Path start) throws IOException { try (Stream<Path> dirs = Files.walk(start)) { dirs.filter(Files::isDirectory).forEach(dir -> { try { dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY); } catch (IOException ignored) {} }); } }
    private void processWatchEvents() { while (watchService!=null && !Thread.currentThread().isInterrupted()) { WatchKey key; try { key = watchService.take(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; } catch (ClosedWatchServiceException cwse) { break; } Path dir = (Path) key.watchable(); boolean refreshNeeded = false; for (WatchEvent<?> event: key.pollEvents()) { WatchEvent.Kind<?> kind = event.kind(); if (kind == StandardWatchEventKinds.OVERFLOW) continue; Path name = (Path) event.context(); Path child = dir.resolve(name); if (kind == StandardWatchEventKinds.ENTRY_CREATE) { if (Files.isDirectory(child)) { try { registerAll(child); } catch (IOException ignored) {} refreshNeeded = true; } else if (isImageFile(child)) refreshNeeded = true; } else if (kind == StandardWatchEventKinds.ENTRY_DELETE || kind == StandardWatchEventKinds.ENTRY_MODIFY) { if (Files.isDirectory(child) || isImageFile(child)) refreshNeeded = true; } } boolean valid = key.reset(); if (refreshNeeded) rebuildFileList(); if (!valid) rebuildFileList(); } }
    private void stopWatcher() { if (watchTask!=null) { watchTask.cancel(true); watchTask=null; } if (watchService!=null) { try { watchService.close(); } catch (IOException ignored) {} watchService=null; } }

    @FXML private void onShuffle() { synchronized (this) { if (imageFiles.isEmpty()) return; Collections.shuffle(imageFiles, random); shuffledMode = true; if (lastShown!=null) currentIndex = imageFiles.indexOf(lastShown); else currentIndex = -1; } Platform.runLater(()-> { observableImages.setAll(new ArrayList<>(imageFiles)); status("Shuffled"); }); }

    @FXML private void onAbout() { Platform.runLater(() -> { Alert alert = new Alert(Alert.AlertType.INFORMATION); alert.setTitle("About GlideWall"); alert.setHeaderText("GlideWall – Random / Sequential Image Slideshow"); StringBuilder sb = new StringBuilder(); sb.append("GlideWall lets you display all images inside a chosen folder and its subfolders as an auto-updating slideshow.\n\n") .append("Key Features:\n") .append(" • Choose Folder: pick the root directory to scan recursively.\n") .append(" • Auto Detection: newly added or removed images are detected automatically (file watcher + periodic rescan).\n") .append(" • Interval: set seconds between slides (spinner).\n") .append(" • Start / Stop: control the slideshow playback.\n") .append(" • Sequential Loop: images advance in order (or shuffled order if shuffle used).\n") .append(" • Shuffle: randomize current list; new additions trigger auto-reshuffle.\n") .append(" • Thumbnails: left panel shows all images; click to jump instantly.\n") .append(" • Fullscreen: toggle with the button; press ESC to exit; UI & list hide in fullscreen.\n") .append(" • Status Bar: shows total file count and currently displayed image.\n") .append(" • Manual Refresh: force rebuild of the list.\n\n") .append("Usage Tips:\n") .append("1. Click 'Choose Folder' first.\n") .append("2. Adjust the interval if desired.\n") .append("3. Press Start; use Shuffle any time.\n") .append("4. Add images to the folder tree – they appear automatically and reshuffle if new.\n") .append("5. Use Fullscreen for a clean display (ESC to exit).\n\n") .append("License: AGPL v3 – strong copyleft for network services.\n") .append("Developed with assistance from AI tooling.\n\n") .append("Open the license URL below for full terms."); TextArea ta = new TextArea(sb.toString()); ta.setEditable(false); ta.setWrapText(true); ta.setPrefRowCount(18); Hyperlink link = new Hyperlink("https://www.gnu.org/licenses/agpl-3.0.html"); link.setOnAction(e -> { try { java.awt.Desktop.getDesktop().browse(java.net.URI.create(link.getText())); } catch (Exception ignored) {} }); VBox box = new VBox(8, ta, link); box.setPrefWidth(640); alert.getDialogPane().setContent(box); alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE); alert.showAndWait(); }); }

    private boolean isImageFile(Path p) { String name = p.getFileName().toString().toLowerCase(); return name.endsWith(".jpg")|| name.endsWith(".jpeg")|| name.endsWith(".png")|| name.endsWith(".gif")|| name.endsWith(".bmp")|| name.endsWith(".webp"); }
    private void status(String msg) { Platform.runLater(() -> statusLabel.setText(msg)); }
    private void cancelTask(ScheduledFuture<?> task) { if (task!=null) task.cancel(false); }
    @FXML private void onToggleFullscreen() { if (imageView.getScene()==null) return; Stage stage = (Stage) imageView.getScene().getWindow(); boolean newState = !stage.isFullScreen(); if (newState) { applyFullscreenUI(true); stage.setFullScreenExitHint(""); } else applyFullscreenUI(false); stage.setFullScreen(newState); }
    private void applyFullscreenUI(boolean full) { Platform.runLater(()-> { if (fullScreenButton!=null) fullScreenButton.setText(full? "Windowed":"Fullscreen"); if (toolBar!=null){ toolBar.setVisible(!full); toolBar.setManaged(!full);} if (thumbList!=null){ thumbList.setVisible(!full); thumbList.setManaged(!full);} if (statusBar!=null){ statusBar.setVisible(!full); statusBar.setManaged(!full);} if (rootPane!=null) rootPane.setStyle("-fx-background-color: black;"); updateAvailableImageHeight(); }); }
    public void shutdown() { onStop(); stopWatcher(); if (scheduler!=null) scheduler.shutdownNow(); }
    @FXML private void onManualRefresh() { rebuildFileList(); status("Refreshed"); }
    private void updateAvailableImageHeight() { if (rootPane==null || imageView==null) return; double total = rootPane.getHeight(); double top = (toolBar!=null && toolBar.isVisible())? toolBar.getHeight():0; double bottom = (statusBar!=null && statusBar.isVisible())? statusBar.getHeight():0; double padding = 10; double available = total - top - bottom - padding; if (available <0) available = 0; imageView.setFitHeight(available); }
    private void updatePlaceholderVisibility() { if (placeholderLabel==null) return; boolean noImages; synchronized (this) { noImages = imageFiles.isEmpty(); } boolean hasDisplayed = imageView!=null && imageView.getImage()!=null; boolean show = noImages || !hasDisplayed; placeholderLabel.setVisible(show); placeholderLabel.setManaged(show); if (noImages) { if (rootDirectory==null) placeholderLabel.setText("Click 'Choose Folder' to select a folder. Images inside it and its subfolders will play here."); else placeholderLabel.setText("No images found in the selected folder. Add images or choose another folder."); } }
}

