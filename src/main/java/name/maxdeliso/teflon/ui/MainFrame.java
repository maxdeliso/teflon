package name.maxdeliso.teflon.ui;

import java.awt.BorderLayout;
import java.io.IOException;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import static java.util.Optional.ofNullable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static name.maxdeliso.teflon.Main.BUFFER_LENGTH;
import static name.maxdeliso.teflon.Main.DEFAULT_UDP_PORT;
import static name.maxdeliso.teflon.Main.MESSAGE_MARSHALLER;
import static name.maxdeliso.teflon.Main.MULTICAST_IPV4_BIND_ADDRESS;
import static name.maxdeliso.teflon.Main.MULTICAST_IPV6_BIND_ADDRESS;
import static name.maxdeliso.teflon.Main.TRANSFER_QUEUE;
import name.maxdeliso.teflon.commands.CommandProcessor;
import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.data.MessageTracker;
import name.maxdeliso.teflon.net.ConnectionManager;
import name.maxdeliso.teflon.net.ConnectionResult;
import name.maxdeliso.teflon.net.NetSelector;
import name.maxdeliso.teflon.net.NetworkInterfaceManager;

/**
 * Main application window for the Teflon chat client.
 * Handles the UI layout, network connections, and message display.
 */
public class MainFrame extends JFrame {
    /**
     * Logger for this class.
     */
    private static final Logger LOG = LogManager.getLogger(MainFrame.class);

    /**
     * Default window width.
     */
    private static final int DEFAULT_WIDTH = 800;

    /**
     * Default window height.
     */
    private static final int DEFAULT_HEIGHT = 600;

    /**
     * Title of the main window.
     */
    private static final String WINDOW_TITLE = "Teflon";

    /**
     * Application icon image.
     */
    private static final java.awt.image.BufferedImage APP_ICON =
            ImageLoader.loadImage("/images/icon.jpg", MainFrame.class);

    /**
     * Chat panel for displaying messages.
     */
    private final ChatPanel chatPanel;

    /**
     * Status panel for displaying connection status.
     */
    private final StatusPanel statusPanel;

    /**
     * Message composer for input.
     */
    private final MessageComposer messageComposer;

    /**
     * Menu item for connection.
     */
    private final JMenuItem connectMenuItem;

    /**
     * Menu item for disconnection.
     */
    private final JMenuItem disconnectMenuItem;

    /**
     * Menu item for about dialog.
     */
    private final JMenuItem aboutMenuItem;

    /**
     * Unique identifier for this instance.
     */
    private final UUID uuid;

    /**
     * Consumer for handling messages.
     */
    private final Consumer<Message> messageConsumer;

    /**
     * Executor for network operations.
     */
    private final ExecutorService executor;

    /**
     * Manager for network connections.
     */
    private final ConnectionManager connectionManager;

    /**
     * Manager for network interfaces.
     */
    private final NetworkInterfaceManager networkInterfaceManager;
    /**
     * Message tracker for handling acknowledgments.
     */
    private final MessageTracker messageTracker;
    /**
     * Command processor for handling chat commands.
     */
    private final CommandProcessor commandProcessor;
    /**
     * Current connection result.
     */
    private ConnectionResult connectionResult;
    /**
     * Dialog for connection configuration.
     */
    private ConnectionDialog connectionDialog;
    /**
     * Indicates if the instance is connected.
     */
    private boolean connected;

    /**
     * Creates a new main frame.
     *
     * @param id           Unique identifier for this instance
     * @param msgConsumer  Consumer for handling messages
     * @param executor     Executor for network operations
     * @param connManager  Manager for network connections
     * @param ifaceManager Manager for network interfaces
     */
    public MainFrame(final UUID id,
                     final Consumer<Message> msgConsumer,
                     final ExecutorService executor,
                     final ConnectionManager connManager,
                     final NetworkInterfaceManager ifaceManager) {
        this.uuid = id;
        this.executor = executor;
        this.connectionManager = connManager;
        this.networkInterfaceManager = ifaceManager;
        this.messageTracker = new MessageTracker(id.toString());

        // Initialize UI components first
        this.chatPanel = new ChatPanel();
        this.statusPanel = new StatusPanel();
        this.commandProcessor = new CommandProcessor(msg -> chatPanel.renderSystemEvent("#757575", "System", msg));
        this.messageConsumer = msgConsumer;  // Use the original message consumer directly
        this.messageComposer = new MessageComposer(
                id,
                this.messageConsumer,
                messageTracker,
                commandProcessor,
                chatPanel,
                statusPanel
        );
        this.connectMenuItem = new JMenuItem("Connect...");
        this.disconnectMenuItem = new JMenuItem("Disconnect");
        this.aboutMenuItem = new JMenuItem("About");

        // Set initial status
        this.statusPanel.updateStatus(false, "disconnected");

        // Register built-in commands
        commandProcessor.registerCommand(new name.maxdeliso.teflon.commands.ChatCommand(
                "help",
                "Show this help message",
                messageComposer::displayHelp
        ));
        commandProcessor.registerCommand(new name.maxdeliso.teflon.commands.ChatCommand(
                "status",
                "Display connection status and message statistics",
                messageComposer::displayStatus
        ));
        commandProcessor.registerCommand(new name.maxdeliso.teflon.commands.ChatCommand(
                "html",
                "Display the raw HTML content of the chat panel",
                messageComposer::displayHtml
        ));
        commandProcessor.registerCommand(new name.maxdeliso.teflon.commands.ChatCommand(
                "connect",
                "Open the connection dialog or quick connect with IPv4/IPv6 (usage: /connect [4|6])",
                args -> {
                    List<String> argList = Arrays.asList(args);
                    if (argList.isEmpty()) {
                        showConnectionDialog();
                    } else if (argList.size() == 1) {
                        String version = argList.getFirst();
                        NetworkInterface defaultInterface = networkInterfaceManager.queryInterfaces().getFirst();
                        switch (version) {
                            case "4" -> handleConnectionResult(connectionManager.connectMulticast(
                                    MULTICAST_IPV4_BIND_ADDRESS,
                                    DEFAULT_UDP_PORT,
                                    defaultInterface
                            ).join());
                            case "6" -> handleConnectionResult(connectionManager.connectMulticast(
                                    MULTICAST_IPV6_BIND_ADDRESS,
                                    DEFAULT_UDP_PORT,
                                    defaultInterface
                            ).join());
                            default -> chatPanel
                                    .renderSystemEvent("#C62828", "Error", "Invalid IP version. Use '4' or '6'.");
                        }
                    } else {
                        chatPanel.renderSystemEvent("#C62828", "Error", "Too many arguments. Usage: /connect [4|6]");
                    }
                }
        ));

        commandProcessor.registerCommand(new name.maxdeliso.teflon.commands.ChatCommand(
                "disconnect",
                "Disconnect from the current chat session",
                args -> handleDisconnect()
        ));

        commandProcessor.registerCommand(new name.maxdeliso.teflon.commands.ChatCommand(
                "quit",
                "Exit the application",
                args -> {
                    dispose();
                    System.exit(0);
                }
        ));

        initializeComponents();
        connectMenuItem.setEnabled(true);
        disconnectMenuItem.setEnabled(false);
    }

    /**
     * Gets the current connection result.
     *
     * @return The current connection result, or null if not connected
     */
    public ConnectionResult getConnectionResult() {
        return connectionResult;
    }

    /**
     * Initialize the UI components.
     */
    protected void initializeComponents() {
        setTitle(WINDOW_TITLE);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setLocationRelativeTo(null);
        setIconImage(APP_ICON);

        setJMenuBar(createMenuBar());

        // Set up main layout
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(chatPanel, BorderLayout.CENTER);
        getContentPane().add(statusPanel, BorderLayout.SOUTH);
        getContentPane().add(messageComposer, BorderLayout.NORTH);

        // Set up event handlers
        connectMenuItem.addActionListener(e -> showConnectionDialog());
        disconnectMenuItem.addActionListener(e -> handleDisconnect());
        aboutMenuItem.addActionListener(e -> showAboutDialog());
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenu helpMenu = new JMenu("Help");

        fileMenu.add(connectMenuItem);
        fileMenu.add(disconnectMenuItem);
        helpMenu.add(aboutMenuItem);

        menuBar.add(fileMenu);
        menuBar.add(helpMenu);

        return menuBar;
    }

    /**
     * Gets the connection dialog.
     *
     * @return The connection dialog
     */
    public ConnectionDialog getConnectionDialog() {
        return connectionDialog;
    }

    void showConnectionDialog() {
        if (connectionDialog == null) {
            connectionDialog = new ConnectionDialog(
                    this,
                    networkInterfaceManager.queryInterfaces(),
                    this::handleConnectionResult,
                    connectionManager
            );
        }
        connectionDialog.setVisible(true);
    }

    void handleConnectionResult(final ConnectionResult result) {
        this.connectionResult = result;
        LOG.info("Connection successful: {}", formatMembershipInfo(result));
        CompletableFuture.supplyAsync(() -> createNetSelector(result), executor)
                .thenAccept(selector -> {
                    updateConnectivityState(true);
                    statusPanel.updateStatus(true, "connected: " + formatMembershipInfo(result));
                    chatPanel.renderSystemEvent("#2E7D32", "Connected", formatMembershipInfo(result));
                    if (connectionDialog != null) {
                        connectionDialog.setVisible(false);
                        connectionDialog.dispose();
                        connectionDialog = null;
                    }
                    SwingUtilities.invokeLater(() -> messageComposer.getInputTextField().requestFocusInWindow());
                    try {
                        selector.selectLoop();
                    } catch (IOException e) {
                        handleError(e);
                    }
                })
                .exceptionally(this::handleError);
    }

    /**
     * Builds a NetSelector for reading/writing messages from the multicast.
     */
    private NetSelector createNetSelector(ConnectionResult connectionResult) {
        return new NetSelector(
                BUFFER_LENGTH,
                connectionResult,
                // Incoming message handler
                (_address, bb) -> MESSAGE_MARSHALLER
                        .bufferToMessage(bb)
                        .ifPresent(msg -> SwingUtilities.invokeLater(() -> processIncomingMessage(msg))),
                // Outgoing message supplier
                () -> ofNullable(TRANSFER_QUEUE.poll())
                        .map(MESSAGE_MARSHALLER::messageToBuffer)
                        .orElse(null)
        );
    }

    /**
     * Process an incoming message.
     *
     * @param message The message to process
     */
    public void processIncomingMessage(Message message) {
        if (message.isAcknowledgment()) {
            messageTracker.processAcknowledgment(message);
            chatPanel.renderAcknowledgment(
                    message.type() == Message.MessageType.ACK ? "#2E7D32" : "#C62828",
                    message.originalMessageId().toString(),
                    message.senderId());
        } else {
            messageTracker.trackMessage(message);
            chatPanel.renderMessage(
                    message.type() == Message.MessageType.SYSTEM_EVENT ? "#757575" : message.generateColor(),
                    message.senderId(),
                    message.htmlSafeBody(),
                    new Date());

            // Send acknowledgment for received messages
            Message ack = Message.createAcknowledgment(uuid.toString(), message.messageId(), true);
            messageConsumer.accept(ack);
        }
    }

    private void handleDisconnect() {
        if (connectionResult != null) {
            try {
                connectionResult.getMembershipKey().drop();
                connectionResult = null;
                updateConnectivityState(false);
                statusPanel.updateStatus(false, "disconnected");
                chatPanel.renderSystemEvent("#757575", "Disconnected", "Connection closed");
            } catch (Exception e) {
                LOG.error("Error during disconnect", e);
                statusPanel.updateStatus(false, "error: " + e.getMessage());
            }
        }
    }

    private Void handleError(final Throwable ex) {
        LOG.error("Error in network operation", ex);
        statusPanel.updateStatus(false, "error: " + ex.getMessage());
        chatPanel.renderSystemEvent("#C62828", "Error", ex.getMessage());
        return null;
    }

    public void updateConnectivityState(final boolean isConnected) {
        this.connected = isConnected;
        connectMenuItem.setEnabled(!isConnected);
        disconnectMenuItem.setEnabled(isConnected);
        messageComposer.updateConnectionStatus(isConnected);
    }

    private void showAboutDialog() {
        AboutDialog aboutDialog = new AboutDialog(this);
        aboutDialog.setVisible(true);
    }

    public String formatMembershipInfo(final ConnectionResult result) {
        return String.format("%s:%d on %s",
                result.getMembershipKey().group().getHostAddress(),
                result.getPort(),
                result.getMembershipKey().networkInterface().getName());
    }

    /**
     * Gets the input text field from the message composer.
     *
     * @return The input text field
     */
    public JTextField getInputTextField() {
        return messageComposer.getInputTextField();
    }

    /**
     * Gets the status panel.
     *
     * @return The status panel
     */
    public StatusPanel getStatusPanel() {
        return statusPanel;
    }

    /**
     * Gets the message composer.
     *
     * @return The message composer
     */
    public MessageComposer getMessageComposer() {
        return messageComposer;
    }

    /**
     * Gets the chat panel.
     *
     * @return The chat panel
     */
    public ChatPanel getChatPanel() {
        return chatPanel;
    }

    /**
     * Gets the connect menu item.
     *
     * @return The connect menu item
     */
    public JMenuItem getConnectMenuItem() {
        return connectMenuItem;
    }

    /**
     * Gets the disconnect menu item.
     *
     * @return The disconnect menu item
     */
    public JMenuItem getDisconnectMenuItem() {
        return disconnectMenuItem;
    }

    /**
     * Gets the about menu item.
     *
     * @return The about menu item
     */
    public JMenuItem getAboutMenuItem() {
        return aboutMenuItem;
    }

    @Override
    public void dispose() {
        if (connectionResult != null) {
            try {
                connectionResult.getMembershipKey().drop();
            } catch (Exception e) {
                LOG.error("Error closing connection", e);
            }
        }
        messageTracker.shutdown();
        super.dispose();
    }
}
