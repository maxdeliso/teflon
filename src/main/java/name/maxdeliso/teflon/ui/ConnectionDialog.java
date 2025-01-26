package name.maxdeliso.teflon.ui;

import name.maxdeliso.teflon.Main;
import name.maxdeliso.teflon.net.ConnectionManager;
import name.maxdeliso.teflon.net.ConnectionResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.net.NetworkInterface;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Dialog for configuring and establishing network connections.
 * Allows selection of network interface, IP address, and UDP port.
 */
class ConnectionDialog extends JDialog {
    /**
     * Logger for this class.
     */
    private static final Logger LOG = LogManager.getLogger(ConnectionDialog.class);

    /**
     * Default dialog width.
     */
    private static final int DIALOG_WIDTH = 500;

    /**
     * Default dialog height.
     */
    private static final int DIALOG_HEIGHT = 300;

    /**
     * Grid spacing.
     */
    private static final int GRID_SPACING = 5;

    /**
     * Number of grid rows.
     */
    private static final int GRID_ROWS = 4;

    /**
     * Number of grid columns.
     */
    private static final int GRID_COLS = 2;

    /**
     * Maximum UDP port number.
     */
    private static final int MAX_UDP_PORT = 65535;
    /**
     * Consumer for handling connection results.
     */
    private final Consumer<ConnectionResult> connectionResultConsumer;
    /**
     * List of available network interfaces.
     */
    private final List<NetworkInterface> interfaces;
    /**
     * Manager for handling network connections.
     */
    private final ConnectionManager connectionManager;
    /**
     * Combo box for IP address selection.
     */
    private JComboBox<String> ipAddressComboBox = null;
    /**
     * Text field for custom IP address input.
     */
    private JTextField customIpAddressField = null;
    /**
     * Combo box for network interface selection.
     */
    private JComboBox<String> interfaceComboBox = null;
    /**
     * Text field for UDP port input.
     */
    private JTextField udpPortField = null;
    /**
     * Label for displaying status messages.
     */
    private JLabel statusLabel = null;
    /**
     * Progress bar for connection status.
     */
    private JProgressBar progressBar = null;

    /**
     * Protected constructor for testing purposes.
     */
    protected ConnectionDialog(
            JFrame parent,
            List<NetworkInterface> interfaces,
            Consumer<ConnectionResult> connectionResultConsumer,
            ConnectionManager connectionManager,
            boolean isTest) {
        super(parent, "Connect to IP Address", true);
        this.interfaces = interfaces;
        this.connectionResultConsumer = connectionResultConsumer;
        this.connectionManager = connectionManager;
        if (!isTest) {
            initializeComponents();
        }
    }

    /**
     * Main constructor for the connection dialog.
     */
    public ConnectionDialog(
            JFrame parent,
            List<NetworkInterface> interfaces,
            Consumer<ConnectionResult> connectionResultConsumer,
            ConnectionManager connectionManager) {
        this(parent, interfaces, connectionResultConsumer, connectionManager, false);
    }

    /**
     * Initialize the UI components.
     */
    protected void initializeComponents() {
        this.ipAddressComboBox = createIpAddressComboBox();
        this.customIpAddressField = createCustomIpAddressField();
        this.interfaceComboBox = createInterfaceComboBox();
        this.udpPortField = createUdpPortField();
        this.statusLabel = createStatusLabel();
        this.progressBar = createProgressBar();

        setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
        setLocationRelativeTo(getParent());
        setLayout(new BorderLayout());

        add(createCenterPanel(), BorderLayout.CENTER);
        add(createSouthPanel(), BorderLayout.SOUTH);
    }

    /**
     * Creates the main panel (center) with interface/IP selection.
     *
     * @return The configured center panel with grid layout
     */
    private JPanel createCenterPanel() {
        JPanel centerPanel = new JPanel(new GridLayout(GRID_ROWS, GRID_COLS, GRID_SPACING, GRID_SPACING));

        centerPanel.add(new JLabel("Interface:", SwingConstants.RIGHT));
        centerPanel.add(interfaceComboBox);

        centerPanel.add(new JLabel("IP Address:", SwingConstants.RIGHT));
        centerPanel.add(ipAddressComboBox);

        centerPanel.add(new JLabel("Custom IP:", SwingConstants.RIGHT));
        centerPanel.add(customIpAddressField);

        centerPanel.add(new JLabel("UDP Port:", SwingConstants.RIGHT));
        centerPanel.add(udpPortField);

        return centerPanel;
    }

    /**
     * Creates the south panel with status label, loading bar, and connect button.
     *
     * @return The configured south panel with grid layout
     */
    private JPanel createSouthPanel() {
        // 3) Change the layout to 3 rows to accommodate the progress bar
        JPanel southPanel = new JPanel(new GridLayout(3, 1, GRID_SPACING, GRID_SPACING));

        southPanel.add(statusLabel);

        // Add the progress bar (hidden by default)
        southPanel.add(progressBar);

        JButton connectButton = new JButton("Connect");
        connectButton.addActionListener(this::onConnect);
        southPanel.add(connectButton);

        return southPanel;
    }

    /**
     * Creates a combo box for choosing IPv4, IPv6, or Custom addresses.
     *
     * @return The configured IP address combo box
     */
    private JComboBox<String> createIpAddressComboBox() {
        String[] addresses = new String[]{
                Main.MULTICAST_IPV4_BIND_ADDRESS,
                Main.MULTICAST_IPV6_BIND_ADDRESS,
                "Custom..."
        };
        JComboBox<String> comboBox = new JComboBox<>(addresses);
        comboBox.addActionListener(this::onIpAddressSelection);
        return comboBox;
    }

    /**
     * Custom IP address field (disabled unless "Custom" is selected).
     *
     * @return The configured IP address text field
     */
    private JTextField createCustomIpAddressField() {
        JTextField textField = new JTextField();
        textField.setEnabled(false);
        return textField;
    }

    /**
     * Creates a combo box containing all detected network interfaces by display name.
     *
     * @return The configured network interface combo box
     */
    private JComboBox<String> createInterfaceComboBox() {
        String[] ifaceNames = interfaces.stream()
                .map(NetworkInterface::getDisplayName)
                .toArray(String[]::new);
        return new JComboBox<>(ifaceNames);
    }

    /**
     * Creates a text field pre-populated with the default UDP port.
     *
     * @return The configured UDP port text field
     */
    private JTextField createUdpPortField() {
        return new JTextField(String.valueOf(Main.DEFAULT_UDP_PORT));
    }

    /**
     * Status label used to display messages in the south panel.
     *
     * @return The configured status label
     */
    private JLabel createStatusLabel() {
        return new JLabel(" ", SwingConstants.CENTER);
    }

    /**
     * Creates the progress bar in an indeterminate mode, hidden by default.
     *
     * @return The configured progress bar
     */
    private JProgressBar createProgressBar() {
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setVisible(false);
        return bar;
    }

    /**
     * Called when the IP address combo box selection changes.
     * Enables or disables the custom IP address field accordingly.
     *
     * @param e The action event
     */
    private void onIpAddressSelection(final ActionEvent e) {
        boolean isCustomSelected = Objects.equals(ipAddressComboBox.getSelectedItem(), "Custom");
        customIpAddressField.setEnabled(isCustomSelected);
    }

    /**
     * Called when the user clicks the "Connect" button.
     *
     * @param e The action event
     */
    private void onConnect(final ActionEvent e) {
        String selectedInterface = (String) interfaceComboBox.getSelectedItem();
        String ipSelection = (String) ipAddressComboBox.getSelectedItem();
        String selectedIpAddress = Objects.equals(ipSelection, "Custom")
                ? customIpAddressField.getText()
                : ipSelection;
        String udpPortText = udpPortField.getText();

        if (isValidInput(selectedIpAddress, udpPortText)) {
            int udpPort = Integer.parseInt(udpPortText);
            statusLabel.setText(String.format("Connecting to %s via %s on port %d...",
                    selectedIpAddress, selectedInterface, udpPort));
            NetworkInterface netIf = interfaces.get(interfaceComboBox.getSelectedIndex());
            progressBar.setVisible(true);
            connectToNetwork(selectedIpAddress, udpPort, netIf);
        } else {
            statusLabel.setText("Invalid IP address or UDP port.");
        }
    }

    /**
     * Validates the given IP address string and port.
     *
     * @param ipAddress   The IP address to validate
     * @param udpPortText The UDP port string to validate
     * @return true if both IP address and port are valid
     */
    private boolean isValidInput(final String ipAddress, final String udpPortText) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return false;
        }
        try {
            int port = Integer.parseInt(udpPortText);
            return port >= 0 && port <= MAX_UDP_PORT;
        } catch (NumberFormatException ex) {
            statusLabel.setText("Invalid UDP port number.");
            return false;
        }
    }

    /**
     * Creates a SwingWorker to connect asynchronously to the specified multicast address.
     *
     * @param ipAddress        The IP address to connect to
     * @param udpPort          The UDP port to use
     * @param networkInterface The network interface to use
     */
    private void connectToNetwork(final String ipAddress, final int udpPort,
                                  final NetworkInterface networkInterface) {
        new SwingWorker<ConnectionResult, Void>() {
            @Override
            protected ConnectionResult doInBackground() throws Exception {
                // Blocks in the background thread:
                return connectionManager.connectMulticast(ipAddress, udpPort, networkInterface)
                        .get();
            }

            @Override
            protected void done() {
                try {
                    ConnectionResult result = get();
                    statusLabel.setText("Connected: " + result);
                    connectionResultConsumer.accept(result);
                } catch (InterruptedException | ExecutionException ex) {
                    String msg = "Connection failed: " + ex.getMessage();
                    statusLabel.setText(msg);
                    LOG.error("Failed to connect", ex);
                } finally {
                    progressBar.setVisible(false);
                }
            }
        }.execute();
    }
}
