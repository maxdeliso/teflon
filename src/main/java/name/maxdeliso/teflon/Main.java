package name.maxdeliso.teflon;

import com.beust.jcommander.JCommander;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import name.maxdeliso.teflon.config.Config;
import name.maxdeliso.teflon.config.ConfigLoader;
import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.net.NetSelector;
import name.maxdeliso.teflon.frames.MainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final String CONFIG_PATH = "teflon.json";
    private static ConfigLoader configLoader = new ConfigLoader(GSON);
    private final LinkedBlockingQueue<Message> outgoingMsgQueue;
    private final AtomicBoolean alive = new AtomicBoolean(true);
    private final String localHostId = UUID.randomUUID().toString();
    private final MainFrame mainFrame;
    private final NetSelector netSelector;

    private Main(Config config) {
        outgoingMsgQueue = new LinkedBlockingQueue<>(config.getBacklogLength());
        mainFrame = new MainFrame(outgoingMsgQueue, alive, localHostId);
        netSelector = new NetSelector(alive, mainFrame, outgoingMsgQueue, localHostId, config, GSON);
        mainFrame.setVisible(true);
    }

    public static void main(String[] args) {
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
                    for(NetworkInterface ni :
                            Collections.list(NetworkInterface.getNetworkInterfaces())) {
                        LOG.info("{} - {}", ni.getName(), ni.toString());
                    }
                } catch (final SocketException se) {
                    throw new RuntimeException(se);
                }
                break;


            case "R": // runs the program
                final Optional<Config> config = configLoader.loadFromFile(CONFIG_PATH);
                final Main main = new Main(
                        config.orElseThrow(() -> new IllegalArgumentException("failed to locate config file at: "
                                + CONFIG_PATH)));
                main.loop();
                break;
        }
    }

    private void loop() {
        netSelector.loop();
    }
}
