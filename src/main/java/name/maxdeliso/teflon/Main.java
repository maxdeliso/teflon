package name.maxdeliso.teflon;

import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.frames.MainFrame;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static name.maxdeliso.teflon.config.Config.BACKLOG_LENGTH;

class Main {

    private final LinkedBlockingQueue<Message> outgoingMsgQueue = new LinkedBlockingQueue<>(BACKLOG_LENGTH);
    private final AtomicBoolean alive = new AtomicBoolean(true);
    private final int localHostId = localHostId();
    private final MainFrame mainFrame = new MainFrame(outgoingMsgQueue, alive, localHostId);
    private final EventHandler eventHandler = new EventHandler(alive, mainFrame, outgoingMsgQueue, localHostId);

    private Main() {
        mainFrame.setVisible(true);
    }

    public static void main(String args[]) {
        final Main main = new Main();
        main.loop();
    }

    private void loop() {
        eventHandler.loop();
    }

    private int localHostId() {
        try {
            return InetAddress.getLocalHost().getHostName().hashCode();
        } catch (UnknownHostException uhe) {
            uhe.printStackTrace();
            return 0;
        }
    }
}
