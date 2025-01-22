package name.maxdeliso.teflon.commands;

/**
 * Interface for chat commands.
 * Each command implementation handles a specific chat command (e.g., /help, /status).
 */
public interface Command {
    /**
     * Executes the command with the given arguments.
     *
     * @param args The command arguments
     */
    void execute(String[] args);

    /**
     * Gets the command name (e.g., "help" for /help).
     *
     * @return The command name
     */
    String getName();

    /**
     * Gets the command description for help text.
     *
     * @return The command description
     */
    String getDescription();
}
