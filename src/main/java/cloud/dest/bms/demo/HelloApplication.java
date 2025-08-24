package cloud.dest.bms.demo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.embed.swing.SwingFXUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.awt.Taskbar;
import java.awt.image.BufferedImage;

public class HelloApplication extends Application {
    private SlideshowController controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("slideshow-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        // Add dark theme stylesheet
        var css = HelloApplication.class.getResource("slideshow.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setTitle("GlideWall");
        var icons = IconFactory.createIcons();
        stage.getIcons().setAll(icons); // JavaFX stage icons
        // Export PNG variants to user cache (~/.glidewall/icons)
        Path exportDir = Path.of(System.getProperty("user.home"), ".glidewall", "icons");
        int written = IconExporter.exportPngIcons(exportDir, false);
        System.out.println("[GlideWall] Set " + icons.size() + " stage icons; exported " + written + " PNG files to " + exportDir);
        // Attempt to set AWT Taskbar icon (helps some DEs)
        try {
            if (Taskbar.isTaskbarSupported()) {
                var taskbar = Taskbar.getTaskbar();
                // pick the largest generated icon for clarity
                javafx.scene.image.Image fxImg = icons.get(icons.size()-1);
                BufferedImage awtImg = SwingFXUtils.fromFXImage(fxImg, null);
                taskbar.setIconImage(awtImg);
            }
        } catch (Exception ignored) { }
        stage.setScene(scene);
        stage.show();
        controller = fxmlLoader.getController();
        // In some Linux desktop environments (e.g. GNOME) the window decoration no longer shows an app icon.
        // Verify via task switcher or dock if not visible in the title bar.
    }

    @Override
    public void stop() throws Exception {
        if (controller != null) {
            controller.shutdown();
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch();
    }
}