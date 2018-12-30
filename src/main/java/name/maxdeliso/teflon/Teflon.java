package name.maxdeliso.teflon;

import com.beust.jcommander.JCommander;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import name.maxdeliso.teflon.config.Config;
import name.maxdeliso.teflon.config.ConfigLoader;
import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.data.MessageMarshaller;
import name.maxdeliso.teflon.net.NetSelector;
import name.maxdeliso.teflon.ui.MainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

class Teflon {
    private static final Logger LOG = LoggerFactory.getLogger(Teflon.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final String CONFIG_PATH = "teflon.json";
    private static final ConfigLoader configLoader = new ConfigLoader(GSON);
    private final NetSelector netSelector;
    private final MainFrame mainFrame;

    private Teflon(Config config) throws UnknownHostException {
        final LinkedBlockingQueue<Message> outgoingMsgQueue = new LinkedBlockingQueue<>(config.getBacklogLength());
        final AtomicBoolean alive = new AtomicBoolean(true);
        final String localHostId = UUID.randomUUID().toString();

        mainFrame = new MainFrame(
                outgoingMsgQueue,
                alive,
                localHostId);

        final MessageMarshaller messageMarshaller = new MessageMarshaller(GSON);

        netSelector = new NetSelector(
                alive,
                (mainFrame::queueMessageDisplay),
                outgoingMsgQueue,
                localHostId,
                config,
                messageMarshaller);
    }

    public static void main(String[] args) throws UnknownHostException {
        final Arguments arguments = new Arguments();
        final JCommander jc = new JCommander();

        jc.addObject(arguments);
        jc.parse(args);

        if (arguments.isHelp()) {
            jc.usage();
            return;
        }

        switch (arguments.getMode()) {
            case "L": // lists available network interfaces
                try {
                    for (NetworkInterface ni :
                            Collections.list(NetworkInterface.getNetworkInterfaces())) {
                        LOG.info("{} - {}", ni.getName(), ni.toString());
                    }
                } catch (final SocketException se) {
                    throw new RuntimeException(se);
                }
                break;


            case "R": // runs the program
                final Config config = configLoader
                        .loadFromFile(CONFIG_PATH)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "failed to locate and load config file at: " + CONFIG_PATH));

                final Teflon teflon = new Teflon(config);

                try {
                    teflon.mainFrame.setVisible(true);
                    teflon.netSelector.select();
                } finally {
                    teflon.mainFrame.dispose();
                }
                break;

            default:
                LOG.error("unrecognized mode: {}", arguments.getMode());
        }
    }
}
