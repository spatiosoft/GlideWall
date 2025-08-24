package com.spatiosoft.glidewall;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.image.Image;

import java.io.IOException;

public class HelloApplication extends Application {
    private SlideshowController controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("slideshow-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        var css = HelloApplication.class.getResource("slideshow.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setTitle("GlideWall");
        var iconUrl = HelloApplication.class.getResource("glidewall-icon.png");
        if (iconUrl == null) {
            // Fallback to old package location if file not yet moved
            iconUrl = HelloApplication.class.getResource("/com/spatiosoft/glidewall/glidewall-icon.png");
        }
        if (iconUrl != null) {
            stage.getIcons().add(new Image(iconUrl.toExternalForm()));
        }
        stage.setScene(scene);
        stage.show();
        controller = fxmlLoader.getController();
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
