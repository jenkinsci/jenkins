/*
 * The MIT License
 *
 * Copyright (c) 2025
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
import hudson.ExtensionPoint;
import hudson.cli.CLICommand;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

/**
 * Callback around {@link CLICommand#run()}, each execution generates a new `correlationId` in order to group related events.
 */
public interface CliListener extends ExtensionPoint {

    /**
     * Called before.
     *
     * @param correlationId This value is used to correlate this command event to other, related command events.
     * @param command The command to be executed.
     * @param argsSize Number of arguments passed to the command.
     * @param auth Authenticated user performing the execution.
     *
     * */
    default void onExecution(
            @NonNull String correlationId, @NonNull String command, int argsSize, @CheckForNull Authentication auth) {}

    /**
     * Called after.
     *
     * @param correlationId This value is used to correlate this command event to other, related command events.
     * @param command The executed command.
     * @param argsSize Number of arguments passed to the command.
     * @param auth Authenticated user executing the command.
     * @param exitCode `run` returned exit code.
     * */
    default void onCompleted(
            @NonNull String correlationId,
            @NonNull String command,
            int argsSize,
            @CheckForNull Authentication auth,
            int exitCode) {}

    /**
     * Catch exceptions.
     *
     * @param correlationId This value is used to correlate this command event to other, related command events.
     * @param command The executed command.
     * @param argsSize Number of arguments passed to the command.
     * @param auth Authenticated user executing the command
     * @param expected True when caller is handling the error to return an specific exitCode.
     * @param t Any error during the execution of the command.
     * */
    default void onError(
            @NonNull String correlationId,
            @NonNull String command,
            int argsSize,
            @CheckForNull Authentication auth,
            boolean expected,
            @NonNull Throwable t) {}

    /**
     * Catch bad credentials.
     *
     * @param correlationId This value is used to correlate this command event to other, related command events.
     * @param command The executed command.
     * @param argsSize Number of arguments passed to the command.
     * @param opaqueId ID used on the command output to indicate an authentication error.
     * @param t Authentication error.
     * */
    default void onLoginFailed(
            @NonNull String correlationId,
            @NonNull String command,
            int argsSize,
            @NonNull String opaqueId,
            @NonNull BadCredentialsException authError) {}
}
