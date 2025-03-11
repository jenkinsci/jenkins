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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.cli.CLICommand;
import jenkins.util.Listeners;

/**
 * Callback around {@link CLICommand#run()}
 *
 * @since TODO
 */
public interface CliListener extends ExtensionPoint {

    /**
     * Called before.
     *
     * @param context Information about the command being executed
     * */
    default void onExecution(@NonNull CliContext context) {}

    /**
     * Called after.
     *
     * @param context Information about the command being executed
     * @param exitCode `run` returned exit code.
     * */
    default void onCompleted(@NonNull CliContext context, int exitCode) {}

    /**
     * Catch exceptions.
     *
     * @param context Information about the command being executed
     * @param exitCode `run` returned exit code.
     * @param t Any error during the execution of the command.
     * */
    default void onError(@NonNull CliContext context, int exitCode, @NonNull Throwable t) {}

    /**
     * Fires the {@link #onExecution} event.
     *
     * @param context Information about the command being executed
     * */
    static void fireExecution(@NonNull CliContext context) {
        Listeners.notify(CliListener.class, true, listener -> listener.onExecution(context));
    }

    /**
     * Fires the {@link #onCompleted} event.
     *
     * @param context Information about the command being executed
     * @param exitCode `run` returned exit code.
     * */
    static void fireCompleted(@NonNull CliContext context, int exitCode) {
        Listeners.notify(CliListener.class, true, listener -> listener.onCompleted(context, exitCode));
    }

    /**
     * Fires the {@link #onError} event.
     *
     * @param context Information about the command being executed
     * @param exitCode `run` returned exit code.
     * @param t Any error during the execution of the command.
     * */
    static void fireError(@NonNull CliContext context, int exitCode, @NonNull Throwable t) {
        Listeners.notify(CliListener.class, true, listener -> listener.onError(context, exitCode, t));
    }
}
