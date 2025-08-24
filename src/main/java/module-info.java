module cloud.dest.bms.demo {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop; // still needed for About dialog hyperlink (Desktop)

    opens cloud.dest.bms.demo to javafx.fxml;
    exports cloud.dest.bms.demo;
}