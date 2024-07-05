package name.maxdeliso.teflon.ui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AboutDialog extends JDialog {
    private static final Logger LOG = LogManager.getLogger(AboutDialog.class);

    public AboutDialog(JFrame parent) {
        super(parent, "About Teflon", true);
        setSize(180, 222);
        setLocationRelativeTo(parent);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(createAboutText(), BorderLayout.CENTER);
        add(panel);
    }

    private static JEditorPane buildJEditorPane(String version) {
        JEditorPane editorPane = new JEditorPane();
        editorPane.setContentType("text/html");
        editorPane.setText("<html>" +
                "<body>" +
                "<p>Version <i>" + version + "</i>" +
                "<p>Teflon is a multicast peer to peer chat application suitable for use on a LAN.</p>" +
                "<p>Visit the <a href='https://github.com/maxdeliso/teflon'>website</a> for more information.</p>" +
                "</body>" +
                "</html>");
        editorPane.setEditable(false);
        editorPane.setOpaque(false);
        return editorPane;
    }

    private JEditorPane createAboutText() {
        JEditorPane editorPane = buildJEditorPane(getVersion());
        editorPane.addHyperlinkListener(e -> {
            if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ex) {
                    LOG.error("failed to browse url", ex);
                }
            }
        });
        return editorPane;
    }

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