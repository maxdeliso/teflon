package name.maxdeliso.teflon;

import name.maxdeliso.teflon.data.TeflonMessage;
import name.maxdeliso.teflon.frames.TeflonFrame;

import java.io.*;
import java.net.*;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static name.maxdeliso.teflon.Main.BACKLOG_LENGTH;

/**
 * This class contains the main event loop which checks in memory queues, and performs UDP sending/receiving.
 */
public class EventHandler {
    private static final byte[] TEFLON_SEND_ADDRESS = new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff};
    private static final int TEFLON_PORT = 1337;
    private static final int IO_TIMEOUT_MS = 50;
    private static final int INPUT_BUFFER_LEN = 4096;

    private final AtomicBoolean alive;
    private final TeflonFrame teflonFrame;
    private final LinkedBlockingQueue<TeflonMessage> incomingMsgQueue = new LinkedBlockingQueue<>(BACKLOG_LENGTH);
    private final LinkedBlockingQueue<TeflonMessage> outgoingMsgQueue;
    private final int localHostId;

    /**
     * The EventHandler constructor.
     *
     * @param alive a flag which is set from AWT threads to signal graceful shutdown.
     * @param teflonFrame an AWT frame to display the frontend.
     * @param outgoingMsgQueue a message queue to hold message that have been sent but not over the network.
     * @param localHostId a numeric host identifier.
     */
    EventHandler(final AtomicBoolean alive,
                 final TeflonFrame teflonFrame,
                 final LinkedBlockingQueue<TeflonMessage> outgoingMsgQueue,
                 final int localHostId) {
        this.alive = alive;
        this.teflonFrame = teflonFrame;
        this.outgoingMsgQueue = outgoingMsgQueue;
        this.localHostId = localHostId;
    }

    /**
     * Main event processing loop.
     */
    public void loop() {
        try {
            final DatagramSocket udpSocket = setupSocket();

            while (alive.get()) {
                final Optional<TeflonMessage> newMessage =
                        Optional.ofNullable(incomingMsgQueue.poll(IO_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                newMessage.ifPresent(teflonFrame::queueMessageDisplay);

                final Optional<TeflonMessage> msgOpt = pollForMessage();
                msgOpt.ifPresent(teflonMessage -> sendMessage(udpSocket, teflonMessage));
                final Optional<DatagramPacket> incomingPacketOpt = receiveFromSocket(udpSocket);
                incomingPacketOpt.ifPresent(this::receiveMessage);
            }
        } catch (InterruptedException | IOException exc) {
            exc.printStackTrace();
        } finally {
            teflonFrame.dispose();
        }
    }

    private void receiveMessage(final DatagramPacket datagramPacket) {
        try {
            final ObjectInput datagramInput = new ObjectInputStream(new ByteArrayInputStream(datagramPacket.getData()));
            final TeflonMessage datagramMessage = (TeflonMessage) datagramInput.readObject();

            if (localHostId == datagramMessage.senderId()) {
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

    private Optional<TeflonMessage> pollForMessage() {
        try {
            return Optional.ofNullable(outgoingMsgQueue.poll(IO_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ie) {
            ie.printStackTrace();
            return Optional.empty();
        }
    }

    private void sendMessage(final DatagramSocket udpSocket, TeflonMessage teflonMessage) {
        try {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(teflonMessage);
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
