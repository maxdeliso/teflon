package name.maxdeliso.teflon.ui;

import name.maxdeliso.teflon.net.ConnectionManager;
import name.maxdeliso.teflon.net.ConnectionResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.NetworkInterface;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static name.maxdeliso.teflon.Main.*;

class ConnectionDialog extends JDialog {
    private static final Logger LOG = LogManager.getLogger(ConnectionDialog.class);

    private final JComboBox<String> ipAddressComboBox;
    private final JTextField customIpAddressField;
    private final JComboBox<String> interfaceComboBox;
    private final JTextField udpPortField;
    private final JLabel statusLabel;

    private final Consumer<ConnectionResult> connectionResultConsumer;
    private final List<NetworkInterface> interfaces;
    private final ConnectionManager connectionManager;

    public ConnectionDialog(JFrame parent,
                            List<NetworkInterface> interfaces,
                            Consumer<ConnectionResult> connectionResultConsumer,
                            ConnectionManager connectionManager) {
        this.interfaces = interfaces;
        this.connectionResultConsumer = connectionResultConsumer;
        this.connectionManager = connectionManager;

        this.ipAddressComboBox = createIpAddressComboBox();
        this.customIpAddressField = createCustomIpAddressField();
        this.interfaceComboBox = createInterfaceComboBox();
        this.udpPortField = createUdpPortField();
        this.statusLabel = createStatusLabel();

        setTitle("Connect to IP Address");
        setSize(511, 316);
        setLocationRelativeTo(parent);
        setModal(true);
        setLayout(new BorderLayout());

        add(createCenterPanel(), BorderLayout.CENTER);
        add(createSouthPanel(), BorderLayout.SOUTH);
    }

    private JPanel createCenterPanel() {
        final JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new GridLayout(4, 2));

        final JLabel interfaceLabel = new JLabel("Interface:", SwingConstants.RIGHT);
        centerPanel.add(interfaceLabel);
        centerPanel.add(interfaceComboBox);

        final JLabel ipLabel = new JLabel("IP Address:", SwingConstants.RIGHT);
        centerPanel.add(ipLabel);
        centerPanel.add(ipAddressComboBox);

        final JLabel customIpLabel = new JLabel("Custom IP:", SwingConstants.RIGHT);
        centerPanel.add(customIpLabel);
        centerPanel.add(customIpAddressField);

        final JLabel udpPortLabel = new JLabel("UDP Port:", SwingConstants.RIGHT);
        centerPanel.add(udpPortLabel);
        centerPanel.add(udpPortField);

        return centerPanel;
    }

    private JPanel createSouthPanel() {
        final JPanel southPanel = new JPanel();
        southPanel.setLayout(new GridLayout(2, 1));

        southPanel.add(statusLabel);

        final JButton connectButton = new JButton("Connect");
        connectButton.addActionListener(this::onConnect);
        southPanel.add(connectButton);

        return southPanel;
    }

    private JComboBox<String> createIpAddressComboBox() {
        final JComboBox<String> comboBox = new JComboBox<>(
                new String[]{
                        MULTICAST_IPV4_BIND_ADDRESS,
                        MULTICAST_IPV6_BIND_ADDRESS,
                        "Custom"
                });
        comboBox.addActionListener(this::onIpAddressSelection);
        return comboBox;
    }

    private JTextField createCustomIpAddressField() {
        final JTextField textField = new JTextField();
        textField.setEnabled(false);
        return textField;
    }

    private JComboBox<String> createInterfaceComboBox() {
        final String[] ifaceNames = interfaces
                .stream()
                .map(NetworkInterface::getDisplayName)
                .toList()
                .toArray(new String[0]);
        return new JComboBox<>(ifaceNames);
    }

    private JTextField createUdpPortField() {
        return new JTextField(String.valueOf(DEFAULT_UDP_PORT));
    }

    private JLabel createStatusLabel() {
        return new JLabel("", SwingConstants.CENTER);
    }

    private void onIpAddressSelection(ActionEvent e) {
        customIpAddressField.setEnabled(Objects.equals(ipAddressComboBox.getSelectedItem(), "Custom"));
    }

    private void onConnect(ActionEvent e) {
        final String selectedInterface = (String) interfaceComboBox.getSelectedItem();
        final String selectedIpAddress = Objects.equals(ipAddressComboBox.getSelectedItem(), "Custom")
                ? customIpAddressField.getText()
                : (String) ipAddressComboBox.getSelectedItem();
        final String udpPortText = udpPortField.getText();

        if (isValidInput(selectedIpAddress, udpPortText)) {
            final int udpPort = Integer.parseInt(udpPortText);
            statusLabel.setText(
                    String.format("Connecting to %s via %s on port %d...",
                            selectedIpAddress, selectedInterface, udpPort));
            final NetworkInterface selectedIface = interfaces.get(interfaceComboBox.getSelectedIndex());
            connectToNetwork(selectedIpAddress, udpPort, selectedIface);
        } else {
            statusLabel.setText("Invalid IP address or UDP port.");
        }
    }

    private boolean isValidInput(String ipAddress, String udpPortText) {
        try {
            final int udpPort = Integer.parseInt(udpPortText);
            return ipAddress != null && !ipAddress.isEmpty() && udpPort >= 0 && udpPort <= 65535;
        } catch (NumberFormatException e) {
            statusLabel.setText("Invalid UDP port number.");
            return false;
        }
    }

    private void connectToNetwork(String ipAddress, int udpPort, NetworkInterface networkInterface) {
        new SwingWorker<ConnectionResult, Void>() {
            @Override
            protected ConnectionResult doInBackground() throws Exception {
                return connectionManager.connectMulticast(ipAddress, udpPort, networkInterface).get();
            }

            @Override
            protected void done() {
                try {
                    ConnectionResult result = get();
                    statusLabel.setText(result.toString());
                    connectionResultConsumer.accept(result);
                } catch (InterruptedException | ExecutionException e) {
                    statusLabel.setText("Connection failed: " + e.getMessage());
                    LOG.error("failed to connect", e);
                }
            }
        }.execute();
    }
}