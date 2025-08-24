package cloud.dest.bms.demo;

import javafx.scene.Group;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.transform.Scale;

import java.util.ArrayList;
import java.util.List;

/** Utility to generate JavaFX Image icons programmatically (no raster asset dependency). */
public final class IconFactory {
    private IconFactory() {}

    /** Create a set of sized icons for Stage.setIcons(). */
    public static List<Image> createIcons() {
        int design = 160; // design coordinate system
        int[] sizes = {16,24,32,48,64,96,128,160,256};
        List<Image> images = new ArrayList<>();
        for (int size : sizes) {
            double scale = size / (double) design;
            Group g = buildIconGroup(design);
            g.getTransforms().add(new Scale(scale, scale));
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            WritableImage wi = new WritableImage(size, size);
            g.snapshot(params, wi);
            images.add(wi);
        }
        return images;
    }

    private static Group buildIconGroup(int size) {
        Group g = new Group();
        Rectangle bg = new Rectangle(size, size);
        bg.setArcWidth(size * 0.18);
        bg.setArcHeight(size * 0.18);
        bg.setFill(Color.web("#0b0f17"));
        g.getChildren().add(bg);

        double cell = size / 4.0;
        for (int i=1;i<4;i++) { // horizontal lines
            Line h = new Line(0, cell*i, size, cell*i);
            h.setStroke(Color.web("#1d2935"));
            h.setStrokeWidth(size/53.0);
            h.setStrokeLineCap(StrokeLineCap.SQUARE);
            g.getChildren().add(h);
        }
        for (int i=1;i<4;i++) { // vertical lines
            Line v = new Line(cell*i, 0, cell*i, size);
            v.setStroke(Color.web("#1d2935"));
            v.setStrokeWidth(size/53.0);
            v.setStrokeLineCap(StrokeLineCap.SQUARE);
            g.getChildren().add(v);
        }

        LinearGradient grad = new LinearGradient(0,0,1,1,true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#4f9dff")), new Stop(1, Color.web("#0055aa")));
        LinearGradient gradLite = new LinearGradient(0,0,1,1,true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#74c0ff")), new Stop(1, Color.web("#1b6ec2")));

        SVGPath outer = new SVGPath();
        outer.setContent("M80 22c-31 0-57 26-57 58s26 58 57 58c21 0 37-9 47-24 2.7-4-0.4-9.2-4.9-9.2h-18.4c-2 0-3.6 1-4.7 2.7-5 7.4-12.7 11.3-21.7 11.3-16.7 0-30.3-14.1-30.3-31.5 0-17.3 13.6-31.5 30.3-31.5 10.9 0 19.9 5.4 25.4 14.5H86.2c-2.8 0-5 2.3-5 5.1v14.5c0 2.8 2.2 5.1 5 5.1h45c2.8 0 5-2.3 5-5.1v-6.6c0-33-25.6-58-56.2-58Z");
        outer.setFill(grad);
        SVGPath inner = new SVGPath();
        inner.setContent("M80 37c-23.7 0-43 19.2-43 43s19.3 43 43 43c14 0 25.7-6.5 33.1-17.6H86.2c-8.4 0-15.2-6.8-15.2-15.2v-14.5c0-8.4 6.8-15.2 15.2-15.2h27c-7.2-11-19.1-18.5-33.2-18.5Z");
        inner.setFill(gradLite);
        inner.setOpacity(0.35);

        g.getChildren().addAll(outer, inner);
        return g;
    }
}
