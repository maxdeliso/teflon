package name.maxdeliso.teflon.ui;

import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.net.ConnectionManager;
import name.maxdeliso.teflon.net.ConnectionResult;
import name.maxdeliso.teflon.net.NetSelector;
import name.maxdeliso.teflon.net.NetworkInterfaceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
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

import static name.maxdeliso.teflon.Main.*;

public class MainFrame extends JFrame {
    private static final int FRAME_WIDTH = 512;
    private static final int FRAME_HEIGHT = 316;
    private static final String FRAME_TITLE = "Teflon";
    private static final Logger LOG = LogManager.getLogger(MainFrame.class);

    private final JMenuItem connectMenuItem = new JMenuItem("Connect");
    private final JMenuItem disconnectMenuItem = new JMenuItem("Disconnect");
    private final JMenuItem aboutMenuItem = new JMenuItem("About");

    private final JTextArea outputTextArea = createOutputTextArea();
    private final JTextField statusTextField = createStatusTextField();
    private final JTextField inputTextField = createInputTextField();
    private final DateFormat dateFormat = DateFormat.getInstance();
    private final ExecutorService netExecutor;

    private final UUID uuid;
    private final Consumer<Message> messageConsumer;
    private final ConnectionManager cm;
    private final NetworkInterfaceManager nim;

    private ConnectionDialog connectionDialog = null;
    private ConnectionResult connectionResult = null;

    public MainFrame(final UUID uuid,
                     final Consumer<Message> messageConsumer,
                     ExecutorService netExecutor,
                     ConnectionManager cm,
                     NetworkInterfaceManager nim) {
        this.uuid = uuid;
        this.messageConsumer = messageConsumer;
        this.netExecutor = netExecutor;
        this.cm = cm;
        this.nim = nim;

        setSize(FRAME_WIDTH, FRAME_HEIGHT);
        setTitle(FRAME_TITLE);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setJMenuBar(setupMenuBar());

        add(BorderLayout.PAGE_START, statusTextField);
        add(BorderLayout.CENTER, new JScrollPane(outputTextArea));
        add(BorderLayout.PAGE_END, new JScrollPane(inputTextField));

        updateConnectivityState(false);
    }

    private JTextArea createOutputTextArea() {
        JTextArea textArea = new JTextArea();
        textArea.setLineWrap(true);
        textArea.setEditable(false);
        return textArea;
    }

    private JTextField createStatusTextField() {
        JTextField textField = new JTextField();
        textField.setEditable(false);
        textField.setHorizontalAlignment(SwingConstants.CENTER);
        textField.setText("disconnected");
        return textField;
    }

    private JTextField createInputTextField() {
        JTextField textField = new JTextField();
        textField.setEditable(false);
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(final KeyEvent ke) {
                if (ke.getKeyCode() == KeyEvent.VK_ENTER) {
                    var outgoing = new Message(uuid.toString(), textField.getText());
                    messageConsumer.accept(outgoing);
                    textField.setText("");
                }
            }
        });
        return textField;
    }

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

    private void showConnectionModal() {
        if (connectionDialog == null) {
            connectionDialog = new ConnectionDialog(
                    this,
                    nim.queryInterfaces(),
                    this::handleConnectionResult,
                    cm);
        }
        connectionDialog.setVisible(true);
    }

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

    private NetSelector createNetSelector(ConnectionResult connectionResult) {
        return new NetSelector(
                BUFFER_LENGTH,
                connectionResult,
                (_address, bb) -> MESSAGE_MARSHALLER
                        .bufferToMessage(bb)
                        .ifPresent(msg -> SwingUtilities.invokeLater(() -> renderMessage(msg))),
                () -> Optional
                        .ofNullable(TRANSFER_QUEUE.poll())
                        .map(MESSAGE_MARSHALLER::messageToBuffer)
                        .orElse(null)
        );
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

    private void renderMessage(final Message msg) {
        final var timeString = dateFormat.format(new Date());
        outputTextArea.append(timeString + " : " + msg.toString() + "\n");
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
