module com.spatiosoft.glidewall {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires javafx.swing; // for SwingFXUtils
    requires com.google.zxing; // QR core
    requires com.google.zxing.javase; // QR helpers (MatrixToImageWriter)

    opens com.spatiosoft.glidewall to javafx.fxml;
    exports com.spatiosoft.glidewall;
}