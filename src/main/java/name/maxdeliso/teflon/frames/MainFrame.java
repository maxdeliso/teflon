package name.maxdeliso.teflon.frames;

import name.maxdeliso.teflon.data.Message;
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
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static name.maxdeliso.teflon.config.Config.TEFLON_HEIGHT;
import static name.maxdeliso.teflon.config.Config.TEFLON_TITLE;
import static name.maxdeliso.teflon.config.Config.TEFLON_WIDTH;

/**
 * A JFrame, which will present a UI through the native windowing system on supported OSes.
 * This frame will accept user input, as well as asynchronously display messages received
 * over UDP (see queueMessageDisplay).
 *
 * @see <a href="https://docs.oracle.com/javase/8/docs/api/javax/swing/JFrame.html">JFrame</a>
 */
public class MainFrame extends JFrame {
    private static final Logger LOG = LoggerFactory.getLogger(MainFrame.class);

    private final JTextArea outputTextArea = new JTextArea();
    private final JPanel headerPanel = new JPanel();
    private final JTextField inputTextField = new JTextField();
    private final DateFormat dateFormat = DateFormat.getInstance();
    private final LinkedBlockingQueue<Message> outgoingMsgQueue;
    private final AtomicBoolean alive;
    private final UUID localHostId;

    public MainFrame(final LinkedBlockingQueue<Message> outgoingMsgQueue,
                     final AtomicBoolean alive,
                     final UUID localHostId) {
        this.outgoingMsgQueue = outgoingMsgQueue;
        this.alive = alive;
        this.localHostId = localHostId;
        buildFrame();
    }

    private void renderMessage(final Message msg, final JTextArea outputArea) {
        final String timeString = dateFormat.format(new Date());
        outputArea.append(timeString + " : " + msg.toString() + "\n");
    }

    private void buildFrame() {
        LOG.debug("building JFrame");
        headerPanel.setLayout(new BorderLayout());
        outputTextArea.setLineWrap(true);
        outputTextArea.setEditable(false);

        inputTextField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(final KeyEvent ke) {
                if (ke.getKeyCode() == KeyEvent.VK_ENTER) {
                    Message outgoingMessage = new Message(localHostId, inputTextField.getText());

                    try {
                        outgoingMsgQueue.put(outgoingMessage);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                        alive.set(false);
                    }

                    renderMessage(outgoingMessage, outputTextArea);
                    inputTextField.setText("");
                }
            }
        });

        setSize(TEFLON_WIDTH, TEFLON_HEIGHT);
        setTitle(TEFLON_TITLE);
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
                alive.set(false);
            }
        });

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        LOG.debug("JFrame built");
    }

    /**
     * Queues up a render of a message in this frame.
     *
     * @param msg the message to render, typically just received from over UDP.
     */
    public void queueMessageDisplay(final Message msg) {
        LOG.debug("queueing message for display");
        SwingUtilities.invokeLater(() -> renderMessage(msg, outputTextArea));
    }
}
