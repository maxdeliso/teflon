package name.maxdeliso.teflon.ui;

import java.awt.BorderLayout;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import static java.util.Objects.requireNonNull;

import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Panel for displaying chat messages.
 * Handles HTML rendering and message formatting.
 */
public class ChatPanel extends JPanel {
    /**
     * Logger for this class.
     */
    private static final Logger LOG = LogManager.getLogger(ChatPanel.class);

    /**
     * Initial HTML template.
     */
    private static final String INITIAL_HTML =
            TemplateLoader.loadTemplate("/templates/initial-template.html", ChatPanel.class);

    /**
     * Message template for HTML formatting.
     */
    private static final String MESSAGE_TEMPLATE =
            TemplateLoader.loadTemplate("/templates/message-template.html", ChatPanel.class);

    /**
     * System event template for HTML formatting.
     */
    private static final String SYSTEM_EVENT_TEMPLATE =
            TemplateLoader.loadTemplate("/templates/system-event-template.html", ChatPanel.class);

    /**
     * Acknowledgment template for HTML formatting.
     */
    private static final String ACK_TEMPLATE =
            TemplateLoader.loadTemplate("/templates/ack-template.html", ChatPanel.class);

    /**
     * Stats template for HTML formatting.
     */
    private static final String STATS_TEMPLATE =
            TemplateLoader.loadTemplate("/templates/status-stats-template.html", ChatPanel.class);

    /**
     * Number of characters to show in truncated sender ID.
     */
    private static final int SENDER_ID_TRUNCATE_LENGTH = 8;

    /**
     * Editor pane for displaying messages.
     */
    private final JEditorPane messagePane;

    /**
     * Date formatter for timestamps.
     */
    private final DateFormat dateFormat;

    /**
     * Current HTML document for efficient message appending.
     */
    private final Document currentDocument;

    /**
     * Creates a new chat panel.
     */
    public ChatPanel() {
        setLayout(new BorderLayout());
        this.dateFormat = new SimpleDateFormat("HH:mm:ss:SS z");
        this.messagePane = createMessagePane();
        this.currentDocument = Jsoup.parse(INITIAL_HTML);

        currentDocument
                .outputSettings()
                .prettyPrint(false)
                .syntax(Document.OutputSettings.Syntax.html)
                .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml);

        add(new JScrollPane(messagePane), BorderLayout.CENTER);
    }

    /**
     * Creates the message display pane.
     *
     * @return The configured editor pane
     */
    private JEditorPane createMessagePane() {
        JEditorPane pane = new JEditorPane();
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setCursor(java.awt.Cursor.getDefaultCursor());
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        pane.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                LOG.debug("Document updated: {}", pane.getText());
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                LOG.debug("Document removed: {}", pane.getText());
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                LOG.debug("Document changed: {}", pane.getText());
            }
        });

        pane.setText(INITIAL_HTML);
        return pane;
    }

    /**
     * Renders a chat message.
     *
     * @param senderId  The sender's ID
     * @param message   The message text
     * @param timestamp The message timestamp
     */
    public void renderMessage(String color, String senderId, String message, Date timestamp) {
        var truncatedId = senderId.substring(0, Math.min(senderId.length(), SENDER_ID_TRUNCATE_LENGTH));
        var colorClass = getColorStyle(color);
        var formattedMessage = String.format(MESSAGE_TEMPLATE,
                colorClass,
                senderId, // Full sender ID for tooltip
                truncatedId,
                dateFormat.format(timestamp),
                message);
        appendToMessagePane(formattedMessage);
    }

    /**
     * Renders a system event message.
     *
     * @param color   The color for the message
     * @param title   The event title
     * @param details The event details
     */
    public void renderSystemEvent(String color, String title, String details) {
        var colorClass = getColorStyle(color);
        var formattedMessage = String.format(SYSTEM_EVENT_TEMPLATE,
                colorClass,
                title,
                dateFormat.format(new Date()),
                details);
        appendToMessagePane(formattedMessage);
    }

    /**
     * Renders an acknowledgment message.
     *
     * @param color     The color for the message
     * @param messageId The message ID
     * @param senderId  The sender's ID
     */
    public void renderAcknowledgment(String color, String messageId, String senderId) {
        var truncatedId = senderId.substring(0, Math.min(senderId.length(), SENDER_ID_TRUNCATE_LENGTH));
        var colorClass = getColorStyle(color);
        var formattedMessage = String.format(ACK_TEMPLATE,
                colorClass,
                messageId,
                truncatedId,
                dateFormat.format(new Date()));
        appendToMessagePane(formattedMessage);
    }

    /**
     * Renders message statistics.
     *
     * @param statusColor      The color to use for the status
     * @param connectionStatus The connection status text
     * @param messagesSent     Number of messages sent
     * @param acksReceived     Number of acknowledgments received
     * @param nacksReceived    Number of negative acknowledgments received
     * @param messagesTimedOut Number of messages that timed out
     * @param pendingMessages  Number of pending messages
     */
    public void renderMessageStats(
            String statusColor,
            String connectionStatus,
            long messagesSent,
            long acksReceived,
            long nacksReceived,
            long messagesTimedOut,
            long pendingMessages) {
        var formattedStats = String.format(
                STATS_TEMPLATE,
                statusColor,
                connectionStatus,
                messagesSent,
                acksReceived,
                nacksReceived,
                messagesTimedOut,
                pendingMessages);
        appendToMessagePane(formattedStats);
    }

    private String getColorStyle(String color) {
        return switch (color) {
            case UIConstants.COLOR_SUCCESS -> "color: #2E7D32;";
            case UIConstants.COLOR_ERROR -> "color: #C62828;";
            case UIConstants.COLOR_NEUTRAL -> "color: #757575;";
            default -> color.startsWith("#") ? "color: " + color + ";" : "color: #757575;";
        };
    }

    /**
     * Appends a message to the message pane.
     *
     * @param message The message to append
     */
    private void appendToMessagePane(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                var messageDoc = Jsoup.parseBodyFragment(message);
                var newMessageElement = requireNonNull(messageDoc.body().children().first());
                currentDocument.body().appendChild(newMessageElement);
                messagePane.setText(currentDocument.html());
                messagePane.setCaretPosition(messagePane.getDocument().getLength());
            } catch (Exception e) {
                LOG.error("Failed to append message", e);
            }
        });
    }

    /**
     * Gets the message pane.
     *
     * @return The message pane
     */
    public JEditorPane getMessagePane() {
        return messagePane;
    }
}
