package name.maxdeliso.teflon.core.di;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dagger.Module;
import dagger.Provides;
import name.maxdeliso.teflon.core.data.JsonMessageMarshaller;
import name.maxdeliso.teflon.core.data.Message;
import name.maxdeliso.teflon.core.net.NetSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

@Module
public class TeflonCoreModule {
    private static final Logger LOG = LoggerFactory.getLogger(TeflonCoreModule.class);

    @Provides
    @Singleton
    BlockingQueue<Message> provideMessageQueue(@Named("teflon.backlogLength") final int backlogLength) {
        return new LinkedBlockingQueue<>(backlogLength);
    }

    @Provides
    @Singleton
    InetAddress provideInetAddress(@Named("teflon.host.address") final String hostAddress) {
        try {
            // NOTE: this causes a DNS Query to run
            return InetAddress.getByName(hostAddress);
        } catch (UnknownHostException uhe) {
            LOG.error("failed to process multicast group from config", uhe);
            throw new RuntimeException(uhe);
        }
    }

    @Provides
    @Singleton
    NetworkInterface provideNetworkInterface(@Named("teflon.host.interface") final String hostInterface) {
        try {
            // NOTE: this invokes some platform specific code
            return NetworkInterface.getByName(hostInterface);
        } catch (final SocketException se) {
            LOG.error("failed to process interface name from config", se);
            throw new RuntimeException(se);
        }
    }

    @Provides
    @Singleton
    NetSelector provideNetSelector(@Named("teflon.udp.port") final int udpPort,
                                   @Named("teflon.udp.bufferLength") final int bufferLength,
                                   final BiConsumer<SocketAddress, byte[]> incomingConsumer,
                                   final InetAddress multicastGroupAddress,
                                   final NetworkInterface multicastInterface,
                                   final Supplier<ByteBuffer> outgoingDataSupplier) {
        return new NetSelector(
                udpPort,
                bufferLength,
                incomingConsumer,
                multicastGroupAddress,
                multicastInterface,
                outgoingDataSupplier);

    }

    @Provides
    @Singleton
    Gson provideGSON() {
        return new GsonBuilder().create();
    }

    @Provides
    @Singleton
    JsonMessageMarshaller provideJsonMessageMarshaller(final Gson gson) {
        return new JsonMessageMarshaller(gson);
    }
}
