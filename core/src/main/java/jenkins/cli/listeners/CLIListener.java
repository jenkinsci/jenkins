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

package jenkins.cli.listeners;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.cli.CLICommand;

/**
 * Allows implementations to listen to {@link CLICommand#run()} execution events.
 *
 * @since 2.503
 */
public interface CLIListener extends ExtensionPoint {

    /**
     * Invoked before command execution.
     *
     * @param context Information about the command being executed.
     * */
    default void onExecution(@NonNull CLIContext context) {}

    /**
     * Invoked after command execution.
     * <p>
     * This method or {@link #onThrowable(CLIContext, Throwable)} will be called, but not both.
     * </p>
     *
     * @param context Information about the command being executed.
     * @param exitCode Exit code returned by the implementation of {@link CLICommand#run()}.
     * */
    default void onCompleted(@NonNull CLIContext context, int exitCode) {}

    /**
     * Invoked when an exception or error occurs during command execution.
     * <p>
     * This method or {@link #onCompleted(CLIContext, int)} will be called, but not both.
     * </p>
     *
     * @param context Information about the command being executed.
     * @param t Any error during the execution of the command.
     * */
    default void onThrowable(@NonNull CLIContext context, @NonNull Throwable t) {}
}
