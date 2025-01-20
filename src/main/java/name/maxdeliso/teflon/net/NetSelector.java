package name.maxdeliso.teflon.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class NetSelector {
    private final int bufferLength;
    private final ConnectionResult connectionResult;
    private final BiConsumer<SocketAddress, ByteBuffer> onIncomingMessage;
    private final Supplier<ByteBuffer> outgoingMessageSupplier;
    private final CompletableFuture<Void> loopFuture = new CompletableFuture<>();

    public NetSelector(final int bufferLength,
                       final ConnectionResult connectionResult,
                       final BiConsumer<SocketAddress, ByteBuffer> incomingConsumer,
                       final Supplier<ByteBuffer> outgoingSupplier) {
        this.bufferLength = bufferLength;
        this.connectionResult = connectionResult;
        this.onIncomingMessage = incomingConsumer;
        this.outgoingMessageSupplier = outgoingSupplier;
    }

    public CompletableFuture<Void> selectLoop() throws IOException {
        var dataBuffer = ByteBuffer.allocateDirect(bufferLength);
        var sendSockAddress = new InetSocketAddress(
                this.connectionResult.getMembershipKey().group(),
                this.connectionResult.getPort());
        var multicastSender = new MulticastSender(connectionResult.getDc(), sendSockAddress);

        try (final var selector = Selector.open()) {
            connectionResult.getDc().register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            while (this.connectionResult.getMembershipKey().isValid() && !Thread.interrupted()) {
                selector.select();
                var selectedKeys = selector.selectedKeys();
                var iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isReadable()) {
                        SocketAddress sender = connectionResult.getDc().receive(dataBuffer);
                        if (sender != null) {
                            dataBuffer.flip();
                            onIncomingMessage.accept(sender, dataBuffer.asReadOnlyBuffer());
                            dataBuffer.clear();
                        }
                    }

                    if (key.isWritable()) {
                        var outgoing = outgoingMessageSupplier.get();
                        if (outgoing != null && outgoing.hasRemaining()) {
                            multicastSender.send(outgoing);
                        }
                    }
                }
            }
        } catch (IOException ioe) {
            loopFuture.completeExceptionally(ioe);
            throw ioe;
        } finally {
            if (!loopFuture.isDone()) {
                loopFuture.complete(null);
            }
        }

        return loopFuture;
    }
}
