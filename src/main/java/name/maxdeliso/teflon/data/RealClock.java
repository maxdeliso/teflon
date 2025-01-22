package name.maxdeliso.teflon.data;

import java.time.Instant;

/**
 * Implementation of Clock that uses the actual system time.
 */
public class RealClock implements Clock {
    @Override
    public Instant now() {
        return Instant.now();
    }
}
