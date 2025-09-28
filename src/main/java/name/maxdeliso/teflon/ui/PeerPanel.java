package name.maxdeliso.teflon.ui;


import javax.swing.*;
import javax.swing.border.TitledBorder;

import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;

import name.maxdeliso.teflon.data.PeerTracker;

/**
 * Panel for displaying known peers in the network.
 * Shows peer UUIDs, IP addresses, and last seen timestamps.
 */
public class PeerPanel extends JPanel {

    /**
     * Default padding for components.
     */
    private static final int DEFAULT_PADDING = 5;

    /**
     * Number of characters to show in truncated UUID.
     */
    private static final int UUID_TRUNCATE_LENGTH = 8;

    /**
     * Date formatter for timestamps.
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * List model for peer display.
     */
    private final DefaultListModel<String> peerListModel;

    /**
     * List component for displaying peers.
     */
    private final JList<String> peerList;

    /**
     * Scroll pane for the peer list.
     */
    private final JScrollPane scrollPane;

    /**
     * Label showing peer count.
     */
    private final JLabel peerCountLabel;

    /**
     * Creates a new peer panel.
     */
    public PeerPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Known Peers",
                TitledBorder.LEFT,
                TitledBorder.TOP
        ));

        this.peerListModel = new DefaultListModel<>();
        this.peerList = createPeerList();
        this.scrollPane = new JScrollPane(peerList);
        this.peerCountLabel = createPeerCountLabel();

        // Set up layout
        add(peerCountLabel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        // Initial state
        updatePeerCount(0);
    }

    /**
     * Creates the peer list component.
     *
     * @return The configured peer list
     */
    private JList<String> createPeerList() {
        JList<String> list = new JList<>(peerListModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new PeerListCellRenderer());
        list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return list;
    }

    /**
     * Creates the peer count label.
     *
     * @return The configured label
     */
    private JLabel createPeerCountLabel() {
        JLabel label = new JLabel("", SwingConstants.CENTER);
        label.setBorder(BorderFactory.createEmptyBorder(
                DEFAULT_PADDING, DEFAULT_PADDING, DEFAULT_PADDING, DEFAULT_PADDING));
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    /**
     * Updates the peer list with current peer information.
     *
     * @param peers Map of peer UUIDs to their information
     */
    public void updatePeers(Map<String, PeerTracker.PeerInfo> peers) {
        SwingUtilities.invokeLater(() -> {
            peerListModel.clear();

            peers.values().stream()
                    .sorted(Comparator.comparing(PeerTracker.PeerInfo::uuid))
                    .forEach(peer -> {
                        String truncatedUuid = peer.uuid().substring(0,
                                Math.min(peer.uuid().length(), UUID_TRUNCATE_LENGTH));
                        String timeStr = peer.lastSeen().atZone(ZoneId.systemDefault()).format(TIME_FORMATTER);
                        String displayText = String.format("%s... %s (%s)",
                                truncatedUuid, peer.ipAddress(), timeStr);
                        peerListModel.addElement(displayText);
                    });

            updatePeerCount(peers.size());
        });
    }

    /**
     * Updates the peer count display.
     *
     * @param count The number of peers
     */
    private void updatePeerCount(int count) {
        String countText = count == 1 ? "1 peer" : count + " peers";
        peerCountLabel.setText(countText);
    }

    /**
     * Custom cell renderer for peer list items.
     */
    private static class PeerListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof String text) {
                setText(text);
                setToolTipText("UUID: " + text.split("\\s+")[0] + "...");
            }

            return this;
        }
    }
}
