package hudson.cli.listeners;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.UUID;
import org.springframework.security.core.Authentication;

/**
 * Holds information of a command execution. Same instance is used to all {@link CliListener} invocations.
 * Use `correlationId` in order to group related events.
 *
 * @since TODO
 */
public class CliContext {
    private final String correlationId = UUID.randomUUID().toString();
    private final String command;
    private final int argsSize;
    private final Authentication auth;

    /**
     * @param command The command being executed.
     * @param argsSize Number of arguments passed to the command.
     * @param auth Authenticated user performing the execution.
     */
    public CliContext(@NonNull String command, int argsSize, @Nullable Authentication auth) {
        this.command = command;
        this.argsSize = argsSize;
        this.auth = auth;
    }

    /**
     * @return Correlate this command event to other, related command events.
     */
    @NonNull
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * @return Command being executed.
     */
    @NonNull
    public String getCommand() {
        return command;
    }

    /**
     * @return Number of arguments passed to the command.
     */
    public int getArgsSize() {
        return argsSize;
    }

    /**
     * @return Authenticated user performing the execution.
     */
    @CheckForNull
    public Authentication getAuth() {
        return auth;
    }
}
