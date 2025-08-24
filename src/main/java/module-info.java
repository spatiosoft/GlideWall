module cloud.dest.bms.demo {
    requires javafx.controls;
    requires javafx.fxml;


    opens cloud.dest.bms.demo to javafx.fxml;
    exports cloud.dest.bms.demo;
}