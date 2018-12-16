package name.maxdeliso.teflon;

import com.beust.jcommander.Parameter;

/**
 * Command line arguments.
 */
public class Arguments {
    @Parameter(names = "--mode", description = "the run mode, one of L or R")
    private String mode = "R";

    @Parameter(names = "--help",
            help = true)
    private boolean help;

    public String getMode() {
        return mode;
    }

    public boolean isHelp() {
        return help;
    }
}
