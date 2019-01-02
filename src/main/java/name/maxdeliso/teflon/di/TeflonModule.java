package name.maxdeliso.teflon.di;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dagger.Module;
import dagger.Provides;
import name.maxdeliso.teflon.RunContext;
import name.maxdeliso.teflon.config.Config;
import name.maxdeliso.teflon.config.ConfigLoader;
import name.maxdeliso.teflon.config.JsonConfigLoader;
import name.maxdeliso.teflon.data.JsonMessageMarshaller;
import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.net.NetSelector;
import name.maxdeliso.teflon.ui.MainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Module
public class TeflonModule {
    private static final Logger LOG = LoggerFactory.getLogger(TeflonModule.class);

    private static final String CONFIG_PATH = "teflon.json";

    @Provides
    @Singleton
    Gson provideGSON() {
        return new GsonBuilder().create();
    }

    @Provides
    @Singleton
    ConfigLoader provideConfigLoader(final Gson gson) {
        return new JsonConfigLoader(gson);
    }

    @Provides
    @Singleton
    JsonMessageMarshaller provideJSONMessageMarshaller(final Gson gson) {
        return new JsonMessageMarshaller(gson);
    }

    @Provides
    @Singleton
    Config provideConfig(final ConfigLoader configLoader) {
        return configLoader.loadFromFile(CONFIG_PATH)
                .orElseThrow(() -> {
                    final String errorMesage = "failed to locate config file at: " + CONFIG_PATH;
                    LOG.error(errorMesage);
                    return new IllegalArgumentException(errorMesage);
                });
    }

    @Provides
    @Singleton
    BlockingQueue<Message> provideMessageQueue(final Config config) {
        return new LinkedBlockingQueue<>(config.getBacklogLength());
    }

    @Provides
    @Singleton
    RunContext provideRunContext() {
        return new RunContext();
    }

    @Provides
    @Singleton
    MainFrame provideMainFrame(final BlockingQueue<Message> messages,
                               final RunContext runContext) {
        return new MainFrame(messages, runContext);
    }

    @Provides
    @Singleton
    InetAddress provideInetAddress(final Config config) {
        try {
            // NOTE: this causes a DNS Query to run
            return InetAddress.getByName(config.getMulticastGroup());
        } catch (UnknownHostException uhe) {
            LOG.error("failed to process multicast group from config", uhe);
            throw new RuntimeException(uhe);
        }
    }


    @Provides
    @Singleton
    NetworkInterface provideNetworkInterface(final Config config) {
        try {
            // NOTE: this invokes some platform specific code
            return NetworkInterface.getByName(config.getInterfaceName());
        } catch (SocketException se) {
            LOG.error("failed to process interface name from config", se);
            throw new RuntimeException(se);
        }
    }

    @Provides
    @Singleton
    NetSelector provideNetSelector(Config config,
                                   RunContext runContext,
                                   JsonMessageMarshaller messageMarshaller,
                                   BlockingQueue<Message> messageQueue,
                                   InetAddress multicastGroupAddress,
                                   NetworkInterface multicastInterface,
                                   MainFrame mainFrame) {
        return new NetSelector(
                runContext.alive(),
                (senderAddress, bytes) -> messageMarshaller
                        .bufferToMessage(bytes)
                        .filter(message -> !(runContext
                                .getLocalHostUUID()
                                .compareTo(message.senderId()) == 0))
                        .ifPresent(mainFrame::queueMessageDisplay),
                multicastGroupAddress,
                multicastInterface,
                () -> Optional.ofNullable(messageQueue.poll())
                        .map(messageMarshaller::messageToBuffer)
                        .orElse(null),
                config);

    }
}
