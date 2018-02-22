package name.maxdeliso.teflon.frames;

import name.maxdeliso.teflon.data.TeflonMessage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A JFrame, which will present a UI through the native windowing system on supported OSes.
 * @see https://docs.oracle.com/javase/8/docs/api/javax/swing/JFrame.html
 * This frame will accept user input, as well as asychronously display messages received over UDP (see queueMessageDisplay).
 */
public class TeflonFrame extends JFrame {
    private static final int TEFLON_WIDTH = 512;
    private static final int TEFLON_HEIGHT = 316;
    private static final String TEFLON_TITLE = "Teflon";

    private final JTextArea outputTextArea = new JTextArea();
    private final JPanel headerPanel = new JPanel();
    private final JTextField inputTextField = new JTextField();
    private final DateFormat dateFormat = DateFormat.getInstance();
    private final LinkedBlockingQueue<TeflonMessage> outgoingMsgQueue;
    private final AtomicBoolean alive;
    private final int localHostId;

    public TeflonFrame(final LinkedBlockingQueue<TeflonMessage> outgoingMsgQueue,
                       final AtomicBoolean alive,
                       final int localHostId) {
        this.outgoingMsgQueue = outgoingMsgQueue;
        this.alive = alive;
        this.localHostId = localHostId;
        buildFrame();
    }

    private void renderMessage(final TeflonMessage msg, final JTextArea outputArea) {
        final String timeString = dateFormat.format(new Date());
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
                    TeflonMessage outgoingMessage = new TeflonMessage(localHostId, inputTextField.getText());

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
    }

    /**
     * Queues up a render of a message in this frame.
     * @param msg the message to render, typically just received from over UDP.
     */
    public void queueMessageDisplay(final TeflonMessage msg) {
        SwingUtilities.invokeLater(() -> renderMessage(msg, outputTextArea));
    }
}
