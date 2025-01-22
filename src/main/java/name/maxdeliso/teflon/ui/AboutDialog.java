package name.maxdeliso.teflon.ui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * Dialog that displays information about the application.
 * Shows version information and links to project resources.
 */
public class AboutDialog extends JDialog {
    /**
     * Logger for this class.
     */
    private static final Logger LOG = LogManager.getLogger(AboutDialog.class);

    /**
     * HTML template for about dialog content.
     */
    private static final String ABOUT_TEMPLATE =
            TemplateLoader.loadTemplate("/templates/about-template.html", AboutDialog.class);

    /**
     * Background image for the dialog.
     */
    private static final BufferedImage BACKGROUND_IMAGE = ImageLoader.loadImage("/images/icon.jpg", AboutDialog.class);

    /**
     * Default dialog width.
     */
    private static final int DIALOG_WIDTH = 400;

    /**
     * Default dialog height.
     */
    private static final int DIALOG_HEIGHT = 300;

    /**
     * Creates a new about dialog.
     *
     * @param parent The parent frame
     */
    public AboutDialog(final JFrame parent) {
        super(parent, "About Teflon", true);
        setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
        setLocationRelativeTo(parent);

        // Create a custom panel with background image
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Draw the background image, scaled to fit the panel
                if (BACKGROUND_IMAGE != null) {
                    Image scaled = BACKGROUND_IMAGE.getScaledInstance(
                            getWidth(), getHeight(), Image.SCALE_SMOOTH);
                    g.drawImage(scaled, 0, 0, this);
                }
            }
        };
        panel.setOpaque(false);

        // Add the about text with transparent background
        JEditorPane aboutText = createAboutText();
        aboutText.setOpaque(false);
        panel.add(aboutText, BorderLayout.CENTER);

        setContentPane(panel);
    }

    /**
     * Creates a JEditorPane with the about text content.
     *
     * @param version The version string to display
     * @return The configured editor pane
     */
    private static JEditorPane buildJEditorPane(final String version) {
        JEditorPane editorPane = new JEditorPane();
        editorPane.setContentType("text/html");
        editorPane.setText(String.format(ABOUT_TEMPLATE, version));
        editorPane.setEditable(false);
        editorPane.setOpaque(false);
        return editorPane;
    }

    /**
     * Creates the about text component with hyperlink support.
     *
     * @return The configured editor pane
     */
    private JEditorPane createAboutText() {
        JEditorPane editorPane = buildJEditorPane(getVersion());
        editorPane.addHyperlinkListener(e -> {
            if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (IOException | URISyntaxException ex) {
                    LOG.error("failed to browse url", ex);
                }
            }
        });
        return editorPane;
    }

    /**
     * Retrieves the application version from the manifest.
     *
     * @return The version string, or "Unknown" if not found
     */
    private String getVersion() {
        String version = "Unknown";
        try (InputStream inputStream = getClass().getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (inputStream != null) {
                Properties properties = new Properties();
                properties.load(inputStream);
                version = properties.getProperty("Implementation-Version", "Unknown");
            }
        } catch (IOException ioe) {
            LOG.error("failed to load version info", ioe);
        }
        return version;
    }
}
