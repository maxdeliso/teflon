package name.maxdeliso.teflon;

import com.beust.jcommander.JCommander;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import name.maxdeliso.teflon.config.Config;
import name.maxdeliso.teflon.config.ConfigLoader;
import name.maxdeliso.teflon.config.JsonConfigLoader;
import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.data.JsonMessageMarshaller;
import name.maxdeliso.teflon.net.NetSelector;
import name.maxdeliso.teflon.ui.MainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Optional.ofNullable;

class Teflon {
    private static final Logger LOG = LoggerFactory.getLogger(Teflon.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final String CONFIG_PATH = "teflon.json";
    private static final ConfigLoader configLoader = new JsonConfigLoader(GSON);
    private final NetSelector netSelector;
    private final MainFrame mainFrame;

    private Teflon(Config config) {
        final LinkedBlockingQueue<Message> outgoingMsgQueue = new LinkedBlockingQueue<>(config.getBacklogLength());
        final AtomicBoolean alive = new AtomicBoolean(true);
        final String localHostId = UUID.randomUUID().toString();

        mainFrame = new MainFrame(
                outgoingMsgQueue,
                alive,
                localHostId);

        final var messageMarshaller = new JsonMessageMarshaller(GSON);

        try {
            // NOTE: this causes a DNS Query to run
            final InetAddress multicastGroupAddress = InetAddress.getByName(config.getMulticastGroup());
            // NOTE: this invokes some platform specific code
            final NetworkInterface multicastInterface = NetworkInterface.getByName(config.getInterfaceName());

            netSelector = new NetSelector(
                    alive,
                    (senderAddress, bytes) -> messageMarshaller
                            .bufferToMessage(bytes)
                            .filter(message -> !(localHostId.compareTo(message.senderId()) == 0))
                            .ifPresent(mainFrame::queueMessageDisplay),
                    multicastGroupAddress,
                    multicastInterface,
                    () -> Optional.ofNullable(outgoingMsgQueue.poll())
                            .map(messageMarshaller::messageToBuffer)
                            .orElse(null),
                    config);
        } catch (final UnknownHostException | SocketException exc) {
            LOG.error("initialization failed", exc);
            throw new RuntimeException(exc);
        }
    }

    public static void main(String[] args) {
        final var arguments = new Arguments();
        final var jc = new JCommander();

        jc.addObject(arguments);
        jc.parse(args);

        final Optional<String> modeOpt = ofNullable(arguments.getMode());

        if (arguments.isHelp() || modeOpt.isEmpty()) {
            jc.usage();
            return;
        }

        switch (modeOpt.get()) {
            case "L": // lists available network interfaces
                try {
                    for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                        LOG.info("{} - {}", ni.getName(), ni.toString());
                    }
                } catch (final SocketException se) {
                    throw new RuntimeException(se);
                }
                break;


            case "R": // runs the program
                final var config = configLoader
                        .loadFromFile(CONFIG_PATH)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "failed to locate and load config file at: " + CONFIG_PATH));

                final var teflon = new Teflon(config);

                try {
                    teflon.mainFrame.setVisible(true);
                    teflon.netSelector.selectLoop();
                } finally {
                    teflon.mainFrame.dispose();
                }
                break;

            default:
                LOG.error("unrecognized mode: {}", arguments.getMode());
        }
    }
}
