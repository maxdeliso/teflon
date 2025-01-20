package name.maxdeliso.teflon.ui;

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

import static name.maxdeliso.teflon.Main.DEFAULT_UDP_PORT;
import static name.maxdeliso.teflon.Main.MULTICAST_IPV4_BIND_ADDRESS;
import static name.maxdeliso.teflon.Main.MULTICAST_IPV6_BIND_ADDRESS;

class ConnectionDialog extends JDialog {
    private static final Logger LOG = LogManager.getLogger(ConnectionDialog.class);

    // Existing fields
    private final JComboBox<String> ipAddressComboBox;
    private final JTextField customIpAddressField;
    private final JComboBox<String> interfaceComboBox;
    private final JTextField udpPortField;
    private final JLabel statusLabel;

    private final Consumer<ConnectionResult> connectionResultConsumer;
    private final List<NetworkInterface> interfaces;
    private final ConnectionManager connectionManager;

    // 1) Add a JProgressBar for a loading animation
    private final JProgressBar progressBar;

    ConnectionDialog(JFrame parent,
                     List<NetworkInterface> interfaces,
                     Consumer<ConnectionResult> connectionResultConsumer,
                     ConnectionManager connectionManager) {
        super(parent, "Connect to IP Address", true);
        this.interfaces = interfaces;
        this.connectionResultConsumer = connectionResultConsumer;
        this.connectionManager = connectionManager;

        this.ipAddressComboBox = createIpAddressComboBox();
        this.customIpAddressField = createCustomIpAddressField();
        this.interfaceComboBox = createInterfaceComboBox();
        this.udpPortField = createUdpPortField();
        this.statusLabel = createStatusLabel();

        // 2) Initialize the progress bar
        this.progressBar = createProgressBar();

        setSize(500, 300);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        add(createCenterPanel(), BorderLayout.CENTER);
        add(createSouthPanel(), BorderLayout.SOUTH);
    }

    /**
     * Creates the main panel (center) with interface/IP selection.
     */
    private JPanel createCenterPanel() {
        JPanel centerPanel = new JPanel(new GridLayout(4, 2, 5, 5));

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
     */
    private JPanel createSouthPanel() {
        // 3) Change the layout to 3 rows to accommodate the progress bar
        JPanel southPanel = new JPanel(new GridLayout(3, 1, 5, 5));

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
     */
    private JComboBox<String> createIpAddressComboBox() {
        JComboBox<String> comboBox = new JComboBox<>(new String[]{
                MULTICAST_IPV4_BIND_ADDRESS,
                MULTICAST_IPV6_BIND_ADDRESS,
                "Custom"
        });
        comboBox.addActionListener(this::onIpAddressSelection);
        return comboBox;
    }

    /**
     * Custom IP address field (disabled unless "Custom" is selected).
     */
    private JTextField createCustomIpAddressField() {
        JTextField textField = new JTextField();
        textField.setEnabled(false);
        return textField;
    }

    /**
     * Creates a combo box containing all detected network interfaces by display name.
     */
    private JComboBox<String> createInterfaceComboBox() {
        String[] ifaceNames = interfaces.stream()
                .map(NetworkInterface::getDisplayName)
                .toArray(String[]::new);
        return new JComboBox<>(ifaceNames);
    }

    /**
     * Creates a text field pre-populated with the default UDP port.
     */
    private JTextField createUdpPortField() {
        return new JTextField(String.valueOf(DEFAULT_UDP_PORT));
    }

    /**
     * Status label used to display messages in the south panel.
     */
    private JLabel createStatusLabel() {
        return new JLabel(" ", SwingConstants.CENTER);
    }

    /**
     * 4) Create the progress bar in an indeterminate mode, hidden by default.
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
     */
    private void onIpAddressSelection(ActionEvent e) {
        boolean isCustomSelected = Objects.equals(ipAddressComboBox.getSelectedItem(), "Custom");
        customIpAddressField.setEnabled(isCustomSelected);
    }

    /**
     * Called when the user clicks the "Connect" button.
     */
    private void onConnect(ActionEvent e) {
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
     */
    private boolean isValidInput(String ipAddress, String udpPortText) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return false;
        }
        try {
            int port = Integer.parseInt(udpPortText);
            return (port >= 0 && port <= 65535);
        } catch (NumberFormatException ex) {
            statusLabel.setText("Invalid UDP port number.");
            return false;
        }
    }

    /**
     * Creates a SwingWorker to connect asynchronously to the specified multicast address.
     */
    private void connectToNetwork(String ipAddress, int udpPort, NetworkInterface networkInterface) {
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
                    // 6) Hide the progress bar when done (success or failure)
                    progressBar.setVisible(false);
                }
            }
        }.execute();
    }
}
