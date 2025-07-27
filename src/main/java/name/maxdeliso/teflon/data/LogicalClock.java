package name.maxdeliso.teflon.data;

import java.time.Instant;

/**
 * A logical clock implementation for testing that allows controlling time.
 */
public class LogicalClock implements Clock {
    private Instant currentTime;

    /**
     * Creates a new logical clock starting at the given time.
     *
     * @param startTime The time to start at
     */
    public LogicalClock(Instant startTime) {
        this.currentTime = startTime;
    }

    @Override
    public Instant now() {
        return currentTime;
    }

    /**
     * Advances the clock by the specified number of seconds.
     *
     * @param seconds Number of seconds to advance
     */
    public void advanceSeconds(long seconds) {
        currentTime = currentTime.plusSeconds(seconds);
    }
}
