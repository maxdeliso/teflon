package name.maxdeliso.teflon.ui;

import java.awt.BorderLayout;
import java.io.IOException;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
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
import name.maxdeliso.teflon.net.QueueMessageSource;

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
    private final ExecutorService netExecutor;

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
     * Current connection result.
     */
    private ConnectionResult connectionResult;
    /**
     * Dialog for connection configuration.
     */
    private ConnectionDialog connectionDialog;

    private volatile NetSelector currentSelector;

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
        this.netExecutor = executor;
        this.connectionManager = connManager;
        this.networkInterfaceManager = ifaceManager;
        this.messageTracker = new MessageTracker(id.toString());

        // Initialize UI components first
        this.chatPanel = new ChatPanel();
        this.statusPanel = new StatusPanel();

        CommandProcessor commandProcessor =
                new CommandProcessor(msg -> chatPanel.renderSystemEvent("#757575", "System", msg));
        this.messageConsumer = msgConsumer;  // Use the original message consumer directly
        this.messageComposer = new MessageComposer(
                id,
                this.messageConsumer,
                messageTracker,
                commandProcessor,
                chatPanel
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
        fileMenu.addSeparator();
        JMenuItem quitMenuItem = new JMenuItem("Quit");
        quitMenuItem.addActionListener(e -> {
            dispose();
            System.exit(0);
        });
        fileMenu.add(quitMenuItem);
        helpMenu.add(aboutMenuItem);

        menuBar.add(fileMenu);
        menuBar.add(helpMenu);

        return menuBar;
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

    private NetSelector createNetSelector(ConnectionResult connectionResult) {
        LOG.debug("Creating NetSelector for connection: {}", formatMembershipInfo(connectionResult));
        try {
            NetSelector selector = new NetSelector(
                    BUFFER_LENGTH,
                    connectionResult,
                    // Incoming message handler
                    (_address, bb) -> MESSAGE_MARSHALLER
                            .bufferToMessage(bb)
                            .ifPresent(msg -> SwingUtilities.invokeLater(() -> processIncomingMessage(msg))),
                    // Outgoing message source
                    new QueueMessageSource(TRANSFER_QUEUE, MESSAGE_MARSHALLER)
            );
            LOG.debug("Successfully created NetSelector");
            return selector;
        } catch (Exception e) {
            LOG.error("Error creating NetSelector: {}", e.getMessage(), e);
            throw e;
        }
    }

    CompletableFuture<Void> handleConnectionResult(final ConnectionResult result) {
        this.connectionResult = result;
        LOG.info("Connection successful: {}", formatMembershipInfo(result));

        // First create and set up the NetSelector
        return CompletableFuture.supplyAsync(() -> {
                    LOG.debug("Starting network operations for connection: {}", formatMembershipInfo(result));
                    try {
                        var membershipKey = result.getMembershipKey();
                        var group = membershipKey.group();
                        var networkInterface = membershipKey.networkInterface();
                        LOG.debug(
                                "Creating NetSelector for group: {} on interface: {}",
                                group,
                                networkInterface.getName()
                        );

                        var selector = createNetSelector(result);
                        LOG.debug("NetSelector created successfully, setting in message composer");

                        // Set the selector and update UI state on EDT
                        SwingUtilities.invokeLater(() -> {
                            currentSelector = selector;
                            messageComposer.setNetSelector(selector);

                            // Now that selector is set up, update UI state
                            updateConnectivityState(true);
                            statusPanel.updateStatus(true, "connected: " + formatMembershipInfo(result));
                            chatPanel.renderSystemEvent("#2E7D32", "Connected", formatMembershipInfo(result));
                            messageComposer.updateConnectionStatus(true);

                            if (connectionDialog != null) {
                                connectionDialog.setVisible(false);
                                connectionDialog.dispose();
                                connectionDialog = null;
                            }

                            messageComposer.getInputTextField().requestFocusInWindow();
                        });
                        return selector;
                    } catch (Exception e) {
                        LOG.error("Error initializing network operations: {} - {}",
                                e.getClass().getName(),
                                e.getMessage(),
                                e);
                        throw e;
                    }
                }, netExecutor)
                .thenCompose(selector -> CompletableFuture.runAsync(() -> {
                    try {
                        LOG.debug("Starting selector loop for group: {} on interface: {}",
                                result.getMembershipKey().group(),
                                result.getMembershipKey().networkInterface().getName());
                        selector.selectLoop().join();
                    } catch (IOException e) {
                        LOG.error("Error in selector loop: {} - {}", e.getClass().getName(), e.getMessage(), e);
                        SwingUtilities.invokeLater(() -> handleError(e));
                        throw new RuntimeException(e);
                    }
                }, netExecutor))
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    LOG.error(
                            "Fatal error in network operations: {} - {}",
                            cause.getClass().getName(),
                            cause.getMessage(),
                            cause
                    );
                    SwingUtilities.invokeLater(() -> handleError(cause));
                    return null;
                });
    }

    /**
     * Process an incoming message.
     *
     * @param message The message to process
     */
    public void processIncomingMessage(Message message) {
        if (message.isAcknowledgment()) {
            messageTracker.processAcknowledgment(message);

            // Filter out self-acknowledgments (caused by IP_MULTICAST_LOOP=true)
            if (!message.senderId().equals(uuid.toString())) {
                chatPanel.renderAcknowledgment(
                        message.type() == Message.MessageType.ACK ? "#2E7D32" : "#C62828",
                        message.originalMessageId().toString(),
                        message.senderId());
            }
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
            if (currentSelector != null) {
                currentSelector.wakeup();
            }
        }
    }

    private void handleDisconnect() {
        if (connectionResult != null) {
            try {
                // First interrupt the selector loop
                NetSelector selector = currentSelector;
                if (selector != null) {
                    LOG.debug("Interrupting selector loop");
                    selector.wakeup(); // Wake up the selector to process the interrupt
                }

                // Drop multicast membership
                if (connectionResult.getMembershipKey() != null) {
                    LOG.debug("Dropping multicast membership");
                    connectionResult.getMembershipKey().drop();
                }

                // Close the datagram channel
                if (connectionResult.getDc() != null) {
                    LOG.debug("Closing datagram channel");
                    connectionResult.getDc().close();
                }
            } catch (IOException e) {
                LOG.error("Error during disconnect", e);
            } finally {
                // Reset state
                LOG.debug("Resetting connection state");
                connectionResult = null;
                currentSelector = null;
                messageComposer.setNetSelector(null);
                updateConnectivityState(false);
                messageComposer.updateConnectionStatus(false);
                statusPanel.updateStatus(false, "disconnected");

                // Update UI
                connectMenuItem.setEnabled(true);
                disconnectMenuItem.setEnabled(false);

                chatPanel.renderSystemEvent("#757575", "System", "Disconnected from chat");
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
