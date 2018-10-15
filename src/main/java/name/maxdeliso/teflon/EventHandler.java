package name.maxdeliso.teflon;

import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.frames.MainFrame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static name.maxdeliso.teflon.config.Config.BACKLOG_LENGTH;
import static name.maxdeliso.teflon.config.Config.INPUT_BUFFER_LEN;
import static name.maxdeliso.teflon.config.Config.IO_TIMEOUT_MS;
import static name.maxdeliso.teflon.config.Config.TEFLON_PORT;
import static name.maxdeliso.teflon.config.Config.TEFLON_SEND_ADDRESS;

/**
 * This class contains the main event loop which checks in memory queues, and performs UDP sending/receiving.
 */
class EventHandler {
    private final AtomicBoolean alive;
    private final MainFrame mainFrame;
    private final LinkedBlockingQueue<Message> incomingMsgQueue = new LinkedBlockingQueue<>(BACKLOG_LENGTH);
    private final LinkedBlockingQueue<Message> outgoingMsgQueue;
    private final int localHostId;

    /**
     * The EventHandler constructor.
     *
     * @param alive            a flag which is set from AWT threads to signal graceful shutdown.
     * @param mainFrame        an AWT frame to display the frontend.
     * @param outgoingMsgQueue a message queue to hold message that have been sent but not over the network.
     * @param localHostId      a numeric host identifier.
     */
    EventHandler(final AtomicBoolean alive,
                 final MainFrame mainFrame,
                 final LinkedBlockingQueue<Message> outgoingMsgQueue,
                 final int localHostId) {
        this.alive = alive;
        this.mainFrame = mainFrame;
        this.outgoingMsgQueue = outgoingMsgQueue;
        this.localHostId = localHostId;
    }

    /**
     * Main event processing loop.
     */
    void loop() {
        try (final DatagramSocket udpSocket = setupSocket()) {
            while (alive.get()) {
                Optional.ofNullable(incomingMsgQueue.poll(IO_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                        .ifPresent(mainFrame::queueMessageDisplay);

                pollForMessage()
                        .ifPresent(message -> sendMessage(udpSocket, message));

                receiveFromSocket(udpSocket)
                        .ifPresent(this::receiveMessage);
            }
        } catch (InterruptedException | IOException exc) {
            exc.printStackTrace();
        } finally {
            mainFrame.dispose();
        }
    }

    private void receiveMessage(final DatagramPacket datagramPacket) {
        try {
            final ObjectInput datagramInput = new ObjectInputStream(new ByteArrayInputStream(datagramPacket.getData()));
            final Message datagramMessage = (Message) datagramInput.readObject();

            if (localHostId == datagramMessage.senderId()) {
                /* Don't receive messages that we ourselves sent */
                return;
            }

            if (!incomingMsgQueue.offer(datagramMessage, IO_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                System.err.println("timed out offering received message to queue... dropping incoming message");
            }
        } catch (IOException | InterruptedException | ClassNotFoundException exc) {
            exc.printStackTrace();
        }
    }

    private DatagramSocket setupSocket() throws SocketException {
        final DatagramSocket udpSocket = new DatagramSocket(TEFLON_PORT);
        udpSocket.setSoTimeout(IO_TIMEOUT_MS);
        udpSocket.setBroadcast(true);
        return udpSocket;
    }

    private Optional<DatagramPacket> receiveFromSocket(final DatagramSocket udpSocket) {
        try {
            final byte[] inputBuffer = new byte[INPUT_BUFFER_LEN];
            final DatagramPacket inputDatagram = new DatagramPacket(inputBuffer, INPUT_BUFFER_LEN);
            udpSocket.receive(inputDatagram);
            return Optional.of(inputDatagram);
        } catch (final SocketTimeoutException ste) {
            return Optional.empty();
        } catch (final IOException ioe) {
            ioe.printStackTrace();
            return Optional.empty();
        }
    }

    private Optional<Message> pollForMessage() {
        try {
            return Optional.ofNullable(outgoingMsgQueue.poll(IO_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ie) {
            ie.printStackTrace();
            return Optional.empty();
        }
    }

    private void sendMessage(final DatagramSocket udpSocket, Message message) {
        try {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(message);
            final byte[] messageBytes = byteArrayOutputStream.toByteArray();

            final DatagramPacket outgoingPacket = new DatagramPacket(
                    messageBytes,
                    messageBytes.length,
                    InetAddress.getByAddress(TEFLON_SEND_ADDRESS),
                    TEFLON_PORT);

            udpSocket.send(outgoingPacket);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
