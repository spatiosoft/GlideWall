module com.spatiosoft.glidewall {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;

    opens com.spatiosoft.glidewall to javafx.fxml;
    exports com.spatiosoft.glidewall;
}