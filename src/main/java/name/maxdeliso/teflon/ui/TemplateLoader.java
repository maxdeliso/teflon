package name.maxdeliso.teflon.ui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for loading HTML templates from resources.
 */
public final class TemplateLoader {
    /**
     * Logger for this class.
     */
    private static final Logger LOG = LogManager.getLogger(TemplateLoader.class);

    /**
     * Private constructor to prevent instantiation.
     */
    private TemplateLoader() {
        // Utility class should not be instantiated
    }

    /**
     * Loads a template from resources, handling null cases.
     *
     * @param templatePath The path to the template resource
     * @param contextClass The class to use for resource loading context
     * @return The loaded template string
     * @throws RuntimeException if template cannot be loaded
     */
    public static String loadTemplate(final String templatePath, final Class<?> contextClass) {
        try (var inputStream = contextClass.getResourceAsStream(templatePath)) {
            if (inputStream == null) {
                throw new IOException("Template resource not found: " + templatePath);
            }
            byte[] bytes = inputStream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Failed to load template: {}", templatePath, e);
            throw new RuntimeException(e);
        }
    }
}
