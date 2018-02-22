package name.maxdeliso.teflon;

import name.maxdeliso.teflon.data.TeflonMessage;
import name.maxdeliso.teflon.frames.TeflonFrame;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    static final int BACKLOG_LENGTH = 1024;

    private final LinkedBlockingQueue<TeflonMessage> outgoingMsgQueue = new LinkedBlockingQueue<>(BACKLOG_LENGTH);
    private final AtomicBoolean alive = new AtomicBoolean(true);
    private final int localHostId = localHostId();
    private final TeflonFrame teflonFrame = new TeflonFrame(outgoingMsgQueue, alive, localHostId);
    private final EventHandler eventHandler = new EventHandler(alive, teflonFrame, outgoingMsgQueue, localHostId);

    private Main() {
        teflonFrame.setVisible(true);
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
