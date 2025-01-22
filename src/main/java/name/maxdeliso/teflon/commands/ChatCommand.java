package name.maxdeliso.teflon.commands;

import java.util.function.Consumer;

/**
 * A basic chat command implementation.
 */
public class ChatCommand implements Command {
    private final String name;
    private final String description;
    private final Consumer<String[]> executor;

    /**
     * Creates a new chat command.
     *
     * @param name        The command name
     * @param description The command description
     * @param executor    The command executor
     */
    public ChatCommand(String name, String description, Consumer<String[]> executor) {
        this.name = name;
        this.description = description;
        this.executor = executor;
    }

    @Override
    public void execute(String[] args) {
        executor.accept(args);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
