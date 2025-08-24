package cloud.dest.bms.demo;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Exports generated stage icons as PNG files so the app ships with raster assets
 * (helpful for window managers / packaging that ignore in-memory icons or SVG).
 */
public final class IconExporter {
    private IconExporter() {}

    /**
     * Generate PNG icon files (if missing) under the provided output directory.
     * @param outputDir base directory to store icons (created if needed)
     * @param overwrite if true, existing files will be regenerated
     * @return number of files written
     */
    public static int exportPngIcons(Path outputDir, boolean overwrite) {
        List<Image> icons = IconFactory.createIcons();
        int[] sizes = {16,24,32,48,64,96,128,160,256};
        if (!Files.exists(outputDir)) {
            try { Files.createDirectories(outputDir); } catch (IOException ignored) { }
        }
        int written = 0;
        for (int i = 0; i < sizes.length && i < icons.size(); i++) {
            int size = sizes[i];
            Path file = outputDir.resolve("glidewall-" + size + ".png");
            if (Files.exists(file) && !overwrite) continue;
            try {
                RenderedImage ri = SwingFXUtils.fromFXImage(icons.get(i), null);
                ImageIO.write(ri, "png", file.toFile());
                written++;
            } catch (IOException ignored) { }
        }
        return written;
    }
}

