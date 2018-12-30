package name.maxdeliso.teflon;

import com.beust.jcommander.Parameter;

/**
 * Command line arguments.
 */
@SuppressWarnings({"unused"})
class Arguments {
    @Parameter(names = "--mode", description = "the run mode, one of L or R")
    private String mode;

    @Parameter(names = "--help", help = true)
    private boolean help;

    String getMode() {
        return mode;
    }

    boolean isHelp() {
        return help;
    }
}
