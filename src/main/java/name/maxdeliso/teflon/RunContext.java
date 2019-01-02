package name.maxdeliso.teflon;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class RunContext {
    private final AtomicBoolean alive;
    private final String localHostUUID;

    public RunContext() {
        alive = new AtomicBoolean(true);
        localHostUUID = UUID.randomUUID().toString();
    }

    public AtomicBoolean alive() {
        return alive;
    }

    public String getLocalHostUUID() {
        return localHostUUID;
    }
}
