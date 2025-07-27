package name.maxdeliso.teflon.ui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import javax.swing.JPanel;
import javax.swing.JTextField;

import name.maxdeliso.teflon.commands.CommandProcessor;
import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.data.MessageTracker;
import name.maxdeliso.teflon.net.NetSelector;

/**
 * Panel for composing and sending messages.
 * Handles text input and command processing.
 */
public class MessageComposer extends JPanel {

    /**
     * Default text field columns.
     */
    private static final int TEXT_FIELD_COLS = 40;

    /**
     * Text field for input.
     */
    private final JTextField inputTextField;

    /**
     * Command processor for handling commands.
     */
    private final CommandProcessor commandProcessor;

    /**
     * Message tracker for handling acknowledgments.
     */
    private final MessageTracker messageTracker;

    /**
     * Consumer for handling messages.
     */
    private final Consumer<Message> messageConsumer;

    /**
     * Instance ID for message tracking.
     */
    private final UUID instanceId;

    /**
     * Chat panel for displaying messages.
     */
    private final ChatPanel chatPanel;

    /**
     * Connection status.
     */
    private boolean connected;

    private volatile NetSelector currentSelector;

    /**
     * Creates a new message composer.
     *
     * @param instanceId       Instance ID for message tracking
     * @param messageConsumer  Consumer for handling messages
     * @param messageTracker   Tracker for message acknowledgments
     * @param commandProcessor Processor for handling commands
     * @param chatPanel        Chat panel for displaying messages
     */
    public MessageComposer(UUID instanceId,
                           Consumer<Message> messageConsumer,
                           MessageTracker messageTracker,
                           CommandProcessor commandProcessor,
                           ChatPanel chatPanel) {
        this.instanceId = instanceId;
        this.messageConsumer = messageConsumer;
        this.messageTracker = messageTracker;
        this.commandProcessor = commandProcessor;
        this.chatPanel = chatPanel;
        this.connected = false;

        setLayout(new BorderLayout());
        this.inputTextField = createInputTextField();
        add(inputTextField, BorderLayout.CENTER);
    }

    /**
     * Creates the input text field.
     *
     * @return The configured text field
     */
    private JTextField createInputTextField() {
        JTextField textField = new JTextField(TEXT_FIELD_COLS);
        textField.addActionListener(this::onInputEnter);
        return textField;
    }

    /**
     * Handles input enter events.
     *
     * @param e The action event
     */
    private void onInputEnter(ActionEvent e) {
        String text = inputTextField.getText().trim();
        if (!text.isEmpty()) {
            processInput(text);
            inputTextField.setText("");
        }
    }

    /**
     * Processes input text.
     *
     * @param text The input text to process
     */
    private void processInput(String text) {
        if (text.startsWith("/")) {
            if (!commandProcessor.processCommand(text)) {
                String errorMessage = "Command not recognized: " + text.substring(1).split("\\s+")[0];
                String escapedMessage = org.apache.commons.text.StringEscapeUtils.escapeHtml4(errorMessage);
                chatPanel.renderSystemEvent("#C62828", "Error", escapedMessage);
            }
        } else if (!text.isEmpty()) {
            if (!connected) {
                String errorMessage = "Message could not be delivered because there is no connection";
                String escapedMessage = org.apache.commons.text.StringEscapeUtils.escapeHtml4(errorMessage);
                chatPanel.renderSystemEvent("#C62828", "Error", escapedMessage);
                return;
            }
            Message message = new Message(instanceId.toString(), text);
            messageTracker.trackMessage(message);

            messageConsumer.accept(message);
            if (currentSelector != null) {
                currentSelector.wakeup();
            }
        }
    }

    /**
     * Displays help information about available commands.
     *
     * @param args Command arguments (unused)
     */
    public void displayHelp(String[] args) {
        chatPanel.renderSystemEvent("#757575", "Help", commandProcessor.getHelpText());
    }

    /**
     * Displays status information including message tracker stats.
     *
     * @param args Command arguments (unused)
     */
    public void displayStatus(String[] args) {
        Map<String, Long> stats = messageTracker.getDeliveryStats();
        String connectionStatus = connected ? "Connected" : "Disconnected";
        String statusColor = connected ? "#2E7D32" : "#C62828";

        chatPanel.renderMessageStats(
            statusColor,
            connectionStatus,
            stats.get("messagesSent"),
            stats.get("acksReceived"),
            stats.get("nacksReceived"),
            stats.get("messagesTimedOut"),
            stats.get("pendingMessages")
        );
    }

    /**
     * Displays the raw HTML content of the chat panel.
     *
     * @param args Command arguments (unused)
     */
    public void displayHtml(String[] args) {
        String html = chatPanel.getMessagePane().getText();
        String escapedHtml = org.apache.commons.text.StringEscapeUtils.escapeHtml4(html);
        chatPanel.renderSystemEvent("#757575", "Chat Panel HTML", escapedHtml);
    }

    /**
     * Updates the connection status.
     *
     * @param isConnected Whether the client is connected
     */
    public void updateConnectionStatus(boolean isConnected) {
        this.connected = isConnected;
    }

    /**
     * Updates the current NetSelector.
     *
     * @param selector The current NetSelector, or null if disconnected
     */
    public void setNetSelector(NetSelector selector) {
        this.currentSelector = selector;
    }

    /**
     * Gets the input text field component.
     *
     * @return The input text field
     */
    public JTextField getInputTextField() {
        return inputTextField;
    }

}

