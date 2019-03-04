package name.maxdeliso.teflon.swing.di;

import com.google.gson.Gson;
import dagger.Module;
import dagger.Provides;
import name.maxdeliso.teflon.core.data.JsonMessageMarshaller;
import name.maxdeliso.teflon.core.data.Message;
import name.maxdeliso.teflon.swing.RunContext;
import name.maxdeliso.teflon.swing.config.JsonTeflonTeflonConfigLoader;
import name.maxdeliso.teflon.swing.config.TeflonConfig;
import name.maxdeliso.teflon.swing.config.TeflonConfigLoader;
import name.maxdeliso.teflon.swing.ui.MainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Module
public class TeflonSwingModule {
    private static final Logger LOG = LoggerFactory.getLogger(TeflonSwingModule.class);

    private static final String CONFIG_PATH = "teflon.json";

    @Provides
    @Singleton
    RunContext provideRunContext() {
        return new RunContext();
    }

    @Provides
    @Singleton
    TeflonConfigLoader provideConfigLoader(final Gson gson) {
        return new JsonTeflonTeflonConfigLoader(gson);
    }

    @Provides
    @Singleton
    TeflonConfig provideConfig(final TeflonConfigLoader configLoader) {
        return configLoader.loadFromFile(CONFIG_PATH)
                .orElseThrow(() -> {
                    final String errorMesage = "failed to locate config file at: " + CONFIG_PATH;
                    LOG.error(errorMesage);
                    return new IllegalArgumentException(errorMesage);
                });
    }

    @Provides
    @Singleton
    @Named("teflon.udp.port")
    int provideUDPPort(final TeflonConfig teflonConfig) {
        return teflonConfig.getUdpPort();
    }

    @Provides
    @Singleton
    @Named("teflon.udp.bufferLength")
    int provideBufferLength(final TeflonConfig teflonConfig) {
        return teflonConfig.getBufferLength();
    }

    @Provides
    @Singleton
    @Named("teflon.udp.backlogLength")
    int provideBacklogLength(final TeflonConfig teflonConfig) {
        return teflonConfig.getBacklogLength();
    }

    @Provides
    @Singleton
    @Named("teflon.host.address")
    String provideHostAddress(final TeflonConfig teflonConfig) {
        return teflonConfig.getHostAddress();
    }

    @Provides
    @Singleton
    @Named("teflon.host.interface")
    String provideHostInterface(final TeflonConfig teflonConfig) {
        return teflonConfig.getInterfaceName();
    }

    // NOTE: this supplies the Swing JFrame with a means to pass a message out
    @Provides
    @Singleton
    Consumer<Message> provideOutgoingMessageConsumer(final TransferQueue<Message> transferQueue) {
        return (message) -> {
            try {
                transferQueue.transfer(message);
            } catch (final InterruptedException ie) {
                throw new RuntimeException(ie);
            }
        };
    }

    @Provides
    @Singleton
    MainFrame provideMainFrame(final RunContext runContext,
                               final Consumer<Message> messageConsumer) {
        return new MainFrame(runContext, messageConsumer);
    }

    // NOTE: this supplies the networking layer with a string of bytes
    @Provides
    @Singleton
    Supplier<ByteBuffer> provideOutgoingMessageSupplier(final TransferQueue<Message> transferQueue,
                                                        final JsonMessageMarshaller jsonMessageMarshaller) {
        return () -> Optional.ofNullable(transferQueue.poll())
                .map(jsonMessageMarshaller::messageToBuffer)
                .orElse(null);
    }

    @Provides
    @Singleton
    BiConsumer<SocketAddress, byte[]> provideIncomingMessageConsumer(final MainFrame mainFrame,
                                                                     final JsonMessageMarshaller jsonMessageMarshaller) {
        return (_addr, message) -> jsonMessageMarshaller
                .bufferToMessage(message)
                .ifPresent(mainFrame::queueMessageDisplay);
    }

    @Provides
    @Singleton
    TransferQueue<Message> provideMessageTransferQueue() {
        return new LinkedTransferQueue<>();
    }
}
