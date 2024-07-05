package name.maxdeliso.teflon.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class NetSelector {
    private final int bufferLength;
    private final ConnectionResult connectionResult;
    private final BiConsumer<SocketAddress, ByteBuffer> incomingByteBufferConsumer;
    private final Supplier<ByteBuffer> outgoingMessageSupplier;

    public NetSelector(final int bufferLength,
                       final ConnectionResult connectionResult,
                       final BiConsumer<SocketAddress, ByteBuffer> incomingConsumer,
                       final Supplier<ByteBuffer> outgoingSupplier) {
        this.bufferLength = bufferLength;
        this.connectionResult = connectionResult;
        this.incomingByteBufferConsumer = incomingConsumer;
        this.outgoingMessageSupplier = outgoingSupplier;
    }

    public CompletableFuture<Void> selectLoop() throws IOException {
        var dataBuffer = ByteBuffer.allocateDirect(bufferLength);
        var sendSockAddress = new InetSocketAddress(
                this.connectionResult.getMembershipKey().group(),
                this.connectionResult.getPort());
        var multicastSender = new MulticastSender(
                connectionResult.getDc(),
                sendSockAddress);

        try (final var selector = Selector.open()) {
            connectionResult.getDc().register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            while (this.connectionResult.getMembershipKey().isValid()) {
                selector.select(0);

                for (final var key : selector.selectedKeys()) {
                    if (key.isReadable()) {
                        var sender = connectionResult.getDc().receive(dataBuffer);
                        dataBuffer.flip();
                        incomingByteBufferConsumer.accept(sender, dataBuffer.asReadOnlyBuffer());
                        dataBuffer.clear();
                    }

                    if (key.isWritable()) {
                        Optional.ofNullable(outgoingMessageSupplier.get())
                                .filter(bb -> bb.array().length > 0)
                                .ifPresent(multicastSender::send);
                    }
                }
            }
        }

        return null;
    }
}
