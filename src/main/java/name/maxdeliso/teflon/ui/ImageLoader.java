package name.maxdeliso.teflon.ui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Utility class for loading images from resources.
 */
public final class ImageLoader {
    /**
     * Logger for this class.
     */
    private static final Logger LOG = LogManager.getLogger(ImageLoader.class);

    /**
     * Private constructor to prevent instantiation.
     */
    private ImageLoader() {
        // Utility class should not be instantiated
    }

    /**
     * Loads an image from resources, handling null cases.
     *
     * @param imagePath    The path to the image resource
     * @param contextClass The class to use for resource loading context
     * @return The loaded BufferedImage
     * @throws RuntimeException if image cannot be loaded
     */
    public static BufferedImage loadImage(final String imagePath, final Class<?> contextClass) {
        try (var imageStream = contextClass.getResourceAsStream(imagePath)) {
            if (imageStream == null) {
                throw new IOException("Image resource not found: " + imagePath);
            }
            return ImageIO.read(imageStream);
        } catch (IOException e) {
            LOG.error("Failed to load image: {}", imagePath, e);
            throw new RuntimeException("Failed to load image: " + e.getMessage(), e);
        }
    }
}
