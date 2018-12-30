package name.maxdeliso.teflon;

import com.beust.jcommander.Parameter;

/**
 * Command line arguments.
 */
@SuppressWarnings({"unused"})
class Arguments {
    @Parameter(names = {"--mode", "-m"}, description = "the run mode, one of L[ist interfaces] or R[un]")
    private String mode;

    @Parameter(names = {"--help", "-h"}, help = true)
    private boolean help;

    String getMode() {
        return mode;
    }

    boolean isHelp() {
        return help;
    }
}
