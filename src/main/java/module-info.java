module cloud.dest.bms.demo {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop; // for java.awt.Desktop browse in About dialog
    requires javafx.swing; // for SwingFXUtils (PNG export)


    opens cloud.dest.bms.demo to javafx.fxml;
    exports cloud.dest.bms.demo;
}