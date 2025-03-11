/*
 * The MIT License
 *
 * Copyright (c) 2025, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.cli.listeners;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.UUID;
import org.springframework.security.core.Authentication;

/**
 * Holds information of a command execution. Same instance is used to all {@link CliListener} invocations.
 * Use `correlationId` in order to group related events to the same command.
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
