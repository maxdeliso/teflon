package name.maxdeliso.teflon.swing.ui;

import name.maxdeliso.teflon.core.data.Message;
import name.maxdeliso.teflon.core.net.NetSelector;
import name.maxdeliso.teflon.swing.RunContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.DateFormat;
import java.util.Date;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A JFrame, which will present a UI through the native windowing system on supported OSes.
 * This frame will accept user input, as well as asynchronously display messages received
 * over UDP (see queueMessageDisplay).
 *
 * @see <a href="https://docs.oracle.com/javase/8/docs/api/javax/swing/JFrame.html">JFrame</a>
 */
public class MainFrame extends JFrame {
    private static final Logger LOG = LoggerFactory.getLogger(MainFrame.class);

    private static final int FRAME_WIDTH = 512;

    private static final int FRAME_HEIGHT = 316;

    private static final String FRAME_TITLE = "Teflon";

    private final JTextArea outputTextArea = new JTextArea();
    private final JPanel headerPanel = new JPanel();
    private final JTextField inputTextField = new JTextField();
    private final DateFormat dateFormat = DateFormat.getInstance();

    private final RunContext runContext;
    private final Consumer<Message> messageConsumer;
    private Supplier<NetSelector> netSelectorSupplier;

    public MainFrame(final RunContext runContext,
                     final Consumer<Message> messageConsumer) {
        this.runContext = runContext;
        this.messageConsumer = messageConsumer;
        buildFrame();
    }

    private void renderMessage(final Message msg, final JTextArea outputArea) {
        final var timeString = dateFormat.format(new Date());
        outputArea.append(timeString + " : " + msg.toString() + "\n");
    }

    private void buildFrame() {
        headerPanel.setLayout(new BorderLayout());
        outputTextArea.setLineWrap(true);
        outputTextArea.setEditable(false);

        inputTextField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(final KeyEvent ke) {
                if (ke.getKeyCode() == KeyEvent.VK_ENTER) {
                    var outgoing = new Message(runContext.getLocalHostUUID(), inputTextField.getText());
                    messageConsumer.accept(outgoing);
                    inputTextField.setText("");
                }
            }
        });

        setSize(FRAME_WIDTH, FRAME_HEIGHT);
        setTitle(FRAME_TITLE);
        setLayout(new BorderLayout());

        add(BorderLayout.PAGE_START, headerPanel);
        add(BorderLayout.CENTER, new JScrollPane(outputTextArea));
        add(BorderLayout.PAGE_END, new JScrollPane(inputTextField));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent we) {
                inputTextField.requestFocus();
            }

            @Override
            public void windowClosed(WindowEvent we) {
                // TODO: figure out a better way to do this
                if (netSelectorSupplier == null) {
                    LOG.error("window closed callback fired, but the net selector supplier was null");
                } else {
                    netSelectorSupplier.get().signalExit();
                }
            }
        });

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    /**
     * Queues up a render of a message in this frame.
     *
     * @param msg the message to render, typically just received from over UDP.
     */
    public void queueMessageDisplay(final Message msg) {
        SwingUtilities.invokeLater(() -> renderMessage(msg, outputTextArea));
    }

    public void setNetSelectorSupplier(final Supplier<NetSelector> netSelectorSupplier) {
        this.netSelectorSupplier = netSelectorSupplier;
    }
}
