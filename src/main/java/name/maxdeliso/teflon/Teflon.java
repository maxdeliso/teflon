package name.maxdeliso.teflon;

import com.beust.jcommander.JCommander;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Optional;

import static java.util.Optional.ofNullable;

class Teflon {
    private static final Logger LOG = LoggerFactory.getLogger(Teflon.class);

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
                final TeflonComponent teflonComponent = DaggerTeflonComponent.builder().build();
                try {

                    teflonComponent.mainFrame().setVisible(true);
                    teflonComponent.netSelector().selectLoop();
                } finally {
                    teflonComponent.mainFrame().dispose();
                }
                break;

            default:
                LOG.error("unrecognized mode: {}", arguments.getMode());
        }
    }
}
