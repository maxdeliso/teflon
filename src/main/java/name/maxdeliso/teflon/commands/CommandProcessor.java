package name.maxdeliso.teflon.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Processes chat commands.
 * Maintains a registry of available commands and handles their execution.
 */
public class CommandProcessor {
    /**
     * Logger for this class.
     */
    private static final Logger LOG = LogManager.getLogger(CommandProcessor.class);

    /**
     * Map of command names to command implementations.
     */
    private final Map<String, Command> commands = new HashMap<>();

    /**
     * Consumer for system messages.
     */
    private final Consumer<String> systemMessageConsumer;

    /**
     * Creates a new command processor.
     *
     * @param systemMessageConsumer Consumer for system messages
     */
    public CommandProcessor(Consumer<String> systemMessageConsumer) {
        this.systemMessageConsumer = systemMessageConsumer;
    }

    /**
     * Registers a command with the processor.
     *
     * @param command The command to register
     */
    public void registerCommand(Command command) {
        commands.put(command.getName().toLowerCase(), command);
    }

    /**
     * Processes a command string.
     *
     * @param commandStr The command string (e.g., "/help args")
     * @return true if the command was handled, false otherwise
     */
    public boolean processCommand(String commandStr) {
        if (!commandStr.startsWith("/")) {
            return false;
        }

        // Remove the leading slash and split into command and args
        String[] parts = commandStr.substring(1).split("\\s+", 2);
        String commandName = parts[0].toLowerCase();
        String[] args = parts.length > 1
                ? parts[1].split("\\s+")
                : new String[0];

        Command command = commands.get(commandName);
        if (command == null) {
            systemMessageConsumer.accept("Unknown command: /" + commandName);
            return false;
        }

        try {
            command.execute(args);
            return true;
        } catch (Exception e) {
            LOG.error("Error executing command: {}", commandName, e);
            systemMessageConsumer.accept("Error executing command: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the help text for all registered commands.
     *
     * @return The help text
     */
    public String getHelpText() {
        StringBuilder helpText = new StringBuilder("Available commands:\n");
        commands.forEach((name, command) -> {
            String commandHelp = String.format("• /%s - %s\n",
                    org.apache.commons.text.StringEscapeUtils.escapeHtml4(name),
                    org.apache.commons.text.StringEscapeUtils.escapeHtml4(command.getDescription()));
            helpText.append(commandHelp);
        });
        return helpText.toString();
    }
}
