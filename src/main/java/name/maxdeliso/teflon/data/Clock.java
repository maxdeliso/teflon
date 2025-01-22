package name.maxdeliso.teflon.data;

import java.time.Instant;

/**
 * Interface for providing time, allowing for both real and logical clocks.
 */
public interface Clock {
    /**
     * Gets the current time.
     *
     * @return The current time as an Instant
     */
    Instant now();
}
