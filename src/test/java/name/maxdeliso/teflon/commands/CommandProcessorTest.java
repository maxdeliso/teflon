package name.maxdeliso.teflon.commands;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the CommandProcessor class.
 */
@ExtendWith(MockitoExtension.class)
class CommandProcessorTest {
    @Mock
    private Consumer<String> systemMessageConsumer;

    @Mock
    private Command testCommand;

    private CommandProcessor commandProcessor;

    @BeforeEach
    void setUp() {
        commandProcessor = new CommandProcessor(systemMessageConsumer);
    }

    @Test
    void testRegisterCommand() {
        when(testCommand.getName()).thenReturn("test");
        commandProcessor.registerCommand(testCommand);
        assertTrue(commandProcessor.processCommand("/test"),
                "Should process registered command");
        verify(testCommand).execute(any());
    }

    @Test
    void testProcessUnknownCommand() {
        assertFalse(commandProcessor.processCommand("/unknown"),
                "Should not process unknown command");
        verify(systemMessageConsumer).accept(contains("Unknown command"));
    }

    @Test
    void testProcessNonCommand() {
        assertFalse(commandProcessor.processCommand("not a command"),
                "Should not process non-command text");
        verify(systemMessageConsumer, never()).accept(anyString());
    }

    @Test
    void testCommandWithArguments() {
        when(testCommand.getName()).thenReturn("test");
        commandProcessor.registerCommand(testCommand);
        commandProcessor.processCommand("/test arg1 arg2");
        verify(testCommand).execute(new String[]{"arg1", "arg2"});
    }

    @Test
    void testCommandError() {
        when(testCommand.getName()).thenReturn("test");
        commandProcessor.registerCommand(testCommand);
        doThrow(new RuntimeException("Test error")).when(testCommand).execute(any());

        commandProcessor.processCommand("/test");
        verify(systemMessageConsumer).accept(contains("Test error"));
    }

    @Test
    void testGetHelpText() {
        when(testCommand.getName()).thenReturn("test");
        when(testCommand.getDescription()).thenReturn("Test command description");
        commandProcessor.registerCommand(testCommand);
        String helpText = commandProcessor.getHelpText();

        assertTrue(helpText.contains("/test"),
                "Help text should contain command name");
        assertTrue(helpText.contains("Test command description"),
                "Help text should contain command description");
    }

    @Test
    void testCommandCaseInsensitivity() {
        when(testCommand.getName()).thenReturn("test");
        commandProcessor.registerCommand(testCommand);
        assertTrue(commandProcessor.processCommand("/TEST"),
                "Should process command regardless of case");
        verify(testCommand).execute(any());
    }

    @Test
    void testMultipleCommands() {
        Command command1 = mock(Command.class);
        Command command2 = mock(Command.class);

        when(command1.getName()).thenReturn("cmd1");
        when(command2.getName()).thenReturn("cmd2");

        commandProcessor.registerCommand(command1);
        commandProcessor.registerCommand(command2);

        commandProcessor.processCommand("/cmd1");
        commandProcessor.processCommand("/cmd2");

        verify(command1).execute(any());
        verify(command2).execute(any());
    }
}