package name.maxdeliso.teflon.ui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;

/**
 * Panel for displaying connection status and message statistics.
 */
public class StatusPanel extends JPanel {
    /**
     * Logger for this class.
     */
    private static final Logger LOG = LogManager.getLogger(StatusPanel.class);

    /**
     * Status template for HTML formatting.
     */
    private static final String STATUS_TEMPLATE =
            TemplateLoader.loadTemplate("/templates/status-template.html", StatusPanel.class);

    /**
     * Default padding for components.
     */
    private static final int DEFAULT_PADDING = 5;

    /**
     * Label for displaying status.
     */
    private final JLabel statusLabel;

    /**
     * Creates a new status panel.
     */
    public StatusPanel() {
        setLayout(new BorderLayout());
        this.statusLabel = createStatusLabel();
        add(statusLabel, BorderLayout.CENTER);
        // Initial status will be empty
    }

    /**
     * Creates the status label.
     *
     * @return The configured status label
     */
    private JLabel createStatusLabel() {
        JLabel label = new JLabel("", SwingConstants.LEFT);
        label.setBorder(BorderFactory.createEmptyBorder(
                DEFAULT_PADDING, DEFAULT_PADDING, DEFAULT_PADDING, DEFAULT_PADDING));
        return label;
    }

    /**
     * Updates the connection status display.
     *
     * @param isConnected    Whether the client is connected
     * @param connectionInfo Connection details to display
     */
    public void updateStatus(boolean isConnected, String connectionInfo) {
        String color = isConnected ? "#2E7D32" : "#C62828";
        String status = isConnected ? "Connected" : "Disconnected";
        updateStatusText(color, status, connectionInfo);
    }

    /**
     * Updates the status text with formatted HTML.
     *
     * @param color   The color to use
     * @param status  The status text
     * @param details Additional details
     */
    private void updateStatusText(String color, String status, String details) {
        Runnable updateText = () -> {
            String formattedStatus = String.format(STATUS_TEMPLATE, color, status, details);
            statusLabel.setText(formattedStatus);
        };

        if (SwingUtilities.isEventDispatchThread()) {
            updateText.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(updateText);
            } catch (Exception e) {
                LOG.error("Failed to update status text", e);
            }
        }
    }
}
