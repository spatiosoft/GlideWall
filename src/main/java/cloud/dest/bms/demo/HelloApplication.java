package cloud.dest.bms.demo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

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