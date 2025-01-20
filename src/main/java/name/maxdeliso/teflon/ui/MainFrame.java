package name.maxdeliso.teflon.ui;

import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.net.ConnectionManager;
import name.maxdeliso.teflon.net.ConnectionResult;
import name.maxdeliso.teflon.net.NetSelector;
import name.maxdeliso.teflon.net.NetworkInterfaceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.ImageIcon;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Image;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static name.maxdeliso.teflon.Main.BUFFER_LENGTH;
import static name.maxdeliso.teflon.Main.MESSAGE_MARSHALLER;
import static name.maxdeliso.teflon.Main.TRANSFER_QUEUE;

public class MainFrame extends JFrame {
    private static final Logger LOG = LogManager.getLogger(MainFrame.class);

    private static final int FRAME_WIDTH = 512;
    private static final int FRAME_HEIGHT = 316;
    private static final String FRAME_TITLE = "Teflon";

    // Menu items
    private final JMenuItem connectMenuItem = new JMenuItem("Connect");
    private final JMenuItem disconnectMenuItem = new JMenuItem("Disconnect");
    private final JMenuItem aboutMenuItem = new JMenuItem("About");

    // Status bar (top)
    private final JTextField statusTextField = createStatusTextField();
    // Networking and domain objects
    private final ExecutorService netExecutor;
    private final UUID uuid;
    private final Consumer<Message> messageConsumer;
    // Input field (bottom)
    private final JTextField inputTextField = createInputTextField();
    private final ConnectionManager cm;
    private final NetworkInterfaceManager nim;
    // A JEditorPane for HTML content
    private final JEditorPane messagePane = createMessagePane();
    // We'll accumulate the conversation in a StringBuilder
    private final StringBuilder htmlBuffer = new StringBuilder("<html><body>");
    // Simple date formatter for incoming messages
    private final DateFormat dateFormat = DateFormat.getTimeInstance(DateFormat.SHORT);
    // Connection dialog
    private ConnectionDialog connectionDialog = null;
    // Holds the current connection info (if any)
    private ConnectionResult connectionResult = null;

    public MainFrame(final UUID uuid,
                     final Consumer<Message> messageConsumer,
                     ExecutorService netExecutor,
                     ConnectionManager connectionManager,
                     NetworkInterfaceManager networkInterfaceManager) {
        this.uuid = uuid;
        this.messageConsumer = messageConsumer;
        this.netExecutor = netExecutor;
        this.cm = connectionManager;
        this.nim = networkInterfaceManager;

        // Set a custom icon from the classpath
        var iconResource = getClass().getResource("/images/icon.jpg");
        if (iconResource != null) {
            Image iconImage = new ImageIcon(iconResource).getImage();
            setIconImage(iconImage);
        }

        // Basic frame settings
        setTitle(FRAME_TITLE);
        setSize(FRAME_WIDTH, FRAME_HEIGHT);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Add top status text
        add(statusTextField, BorderLayout.PAGE_START);

        // Add our message pane in the center
        // A scroll pane containing the messagePane
        javax.swing.JScrollPane messageScrollPane = new javax.swing.JScrollPane(messagePane);
        add(messageScrollPane, BorderLayout.CENTER);

        // Add input field at bottom
        add(new JScrollPane(inputTextField), BorderLayout.PAGE_END);

        // Set up the menu bar
        setJMenuBar(setupMenuBar());

        // Start in disconnected state
        updateConnectivityState(false);
    }

    /**
     * Creates a non-editable text field to show connection status.
     */
    private JTextField createStatusTextField() {
        JTextField textField = new JTextField("disconnected");
        textField.setEditable(false);
        textField.setHorizontalAlignment(SwingConstants.CENTER);
        return textField;
    }

    /**
     * Creates the input field that sends a Message when user presses Enter.
     */
    private JTextField createInputTextField() {
        JTextField textField = new JTextField();
        textField.setEditable(false);
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(final KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    var outgoing = new Message(uuid.toString(), textField.getText());
                    messageConsumer.accept(outgoing);
                    textField.setText("");
                }
            }
        });
        return textField;
    }

    /**
     * Creates a JEditorPane that uses HTML content.
     */
    private JEditorPane createMessagePane() {
        JEditorPane editor = new JEditorPane();
        editor.setContentType("text/html");
        editor.setEditable(false);
        editor.setText("<html><body></body></html>");
        return editor;
    }

    /**
     * Sets up the menu bar with Connect, Disconnect, and About items.
     */
    private JMenuBar setupMenuBar() {
        var mb = new JMenuBar();
        var menu = new JMenu("Main");

        connectMenuItem.addActionListener(ev -> showConnectionModal());
        menu.add(connectMenuItem);

        disconnectMenuItem.addActionListener(ev -> disconnect());
        menu.add(disconnectMenuItem);

        aboutMenuItem.addActionListener(ev -> showAboutDialog());
        menu.add(aboutMenuItem);

        mb.add(menu);
        return mb;
    }

    /**
     * Shows the ConnectionDialog for selecting interface, IP, and port.
     */
    private void showConnectionModal() {
        if (connectionDialog == null) {
            connectionDialog = new ConnectionDialog(
                    this,
                    nim.queryInterfaces(),
                    this::handleConnectionResult,
                    cm
            );
        }
        connectionDialog.setVisible(true);
    }

    /**
     * Called after the user successfully connects in the ConnectionDialog.
     */
    private void handleConnectionResult(ConnectionResult connectionResult) {
        this.connectionResult = connectionResult;
        updateConnectivityState(true);
        updateStatusText("connected: " + connectionResult);

        SwingUtilities.invokeLater(() -> connectionDialog.setVisible(false));

        CompletableFuture
                .supplyAsync(() -> createNetSelector(connectionResult), netExecutor)
                .thenApplyAsync(netSelector -> {
                    try {
                        return netSelector.selectLoop();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, netExecutor)
                .thenAccept(result -> handleDisconnect())
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
                        .ifPresent(msg -> SwingUtilities.invokeLater(() -> renderMessage(msg))),
                // Outgoing message supplier
                () -> Optional
                        .ofNullable(TRANSFER_QUEUE.poll())
                        .map(MESSAGE_MARSHALLER::messageToBuffer)
                        .orElse(null)
        );
    }

    private void renderMessage(final Message msg) {
        var timeString = dateFormat.format(new Date());

        var newMessageHtml =
                "<p style=\"margin:0; padding:0;\">"
                        + "  <span style=\"color:" + msg.generateColor() + "; font-weight:bold;\""
                        + "        title=\"" + msg.senderId() + "\">"
                        + msg.senderId().substring(0, 8)
                        + "  </span>"
                        + "  <span style=\"color:gray; font-size:small;\">&nbsp;[" + timeString + "]</span>"
                        + "  <br/>"
                        + msg.htmlSafeBody()
                        + "</p>";

        htmlBuffer.append(newMessageHtml);
        messagePane.setText(htmlBuffer + "</body></html>");

        SwingUtilities.invokeLater(() -> {
            messagePane.setCaretPosition(messagePane.getDocument().getLength());
        });
    }

    private void disconnect() {
        if (connectionResult != null && connectionResult.getDc().isOpen()) {
            try {
                connectionResult.getDc().close();
                updateStatusText("disconnected");
            } catch (IOException e) {
                LOG.error("error while disconnecting", e);
                updateStatusText("error: " + e.getMessage());
            } finally {
                updateConnectivityState(false);
                connectionResult = null;
            }
        }
    }

    private void handleDisconnect() {
        updateConnectivityState(false);
        updateStatusText("disconnected");
    }

    private Void handleError(Throwable ex) {
        LOG.error("exception in net loop", ex);
        updateConnectivityState(false);
        updateStatusText("error: " + ex.getMessage());
        return null;
    }

    private void updateConnectivityState(boolean isConnected) {
        SwingUtilities.invokeLater(() -> {
            inputTextField.setEditable(isConnected);
            connectMenuItem.setEnabled(!isConnected);
            disconnectMenuItem.setEnabled(isConnected);
        });
    }

    private void updateStatusText(String text) {
        SwingUtilities.invokeLater(() -> {
            statusTextField.setText(text);
        });
    }

    private void showAboutDialog() {
        AboutDialog aboutModal = new AboutDialog(this);
        aboutModal.setVisible(true);
    }
}
