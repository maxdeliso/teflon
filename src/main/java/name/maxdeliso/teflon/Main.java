package name.maxdeliso.teflon;

import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.frames.MainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static name.maxdeliso.teflon.config.Config.BACKLOG_LENGTH;

class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private final LinkedBlockingQueue<Message> outgoingMsgQueue = new LinkedBlockingQueue<>(BACKLOG_LENGTH);
    private final AtomicBoolean alive = new AtomicBoolean(true);
    private final UUID localHostId = localHostId();
    private final MainFrame mainFrame = new MainFrame(outgoingMsgQueue, alive, localHostId);
    private final EventHandler eventHandler = new EventHandler(alive, mainFrame, outgoingMsgQueue, localHostId);

    private Main() {
        mainFrame.setVisible(true);
    }

    public static void main(String[] args) {
        final Main main = new Main();
        LOG.debug("entering main loop");
        main.loop();
        LOG.debug("exiting main loop");
    }

    private void loop() {
        eventHandler.loop();
    }

    private UUID localHostId() {
       return UUID.randomUUID();
    }
}
