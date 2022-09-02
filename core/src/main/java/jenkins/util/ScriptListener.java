/*
 * The MIT License
 *
 * Copyright (c) 2022
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

package jenkins.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.lang.Binding;
import hudson.ExtensionPoint;
import hudson.model.User;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import org.kohsuke.stapler.StaplerRequest;

/**
 * A listener to track in-process script use.
 *
 * <p>Note that (unsandboxed) script execution can easily result in logging configuration being changed, so if you rely
 * on complete logging of scripting actions, make sure to set up logging to remote systems.</p>
 *
 * @see jenkins.model.Jenkins#_doScript(StaplerRequest, org.kohsuke.stapler.StaplerResponse, javax.servlet.RequestDispatcher, hudson.remoting.VirtualChannel, hudson.security.ACL)
 * @see hudson.cli.GroovyCommand
 * @see hudson.cli.GroovyshCommand
 * @see jenkins.util.groovy.GroovyHookScript
 */
public interface ScriptListener extends ExtensionPoint {

    /**
     * Script definition events are everything related to script creation that's not directly executing them.
     * Both configuring a script-based job or build step, and approving previously defined scripts would call this method.
     * This allows listeners to record every user who is involved in a script.
     * If there is no script definition separate from execution (e.g., Script Console), this is not expected to be called.
     * If both script definition and execution happen around the same time (e.g., a Script Console like feature that
     * integrates with Script Security and would call #using before execution), it is up to callers whether they call just
     * {@link #onScriptExecution(String, groovy.lang.Binding, Object, String, hudson.model.User)}
     * or also this.
     *
     * @param script The script.
     * @param context Object representing the script definition context (e.g. {@link hudson.model.Job}).
     *                Can be {@code null} if not applicable (e.g., CLI commands not acting on jobs/builds).
     * @param correlationId This value is used to correlate this script event to other, related script events.
     *                   Callers are expected to provide values that allow receivers to associate script definition (if applicable), execution and output.
     * @param user If available, the user who caused this event. Can be {@code null}.
     */
    default void onScriptDefinition(@NonNull String script, @CheckForNull Object context, @NonNull String correlationId, @CheckForNull User user) {
    }

    /**
     * Called just before scripts are executed.
     *
     * Examples include:
     * <ul>
     *     <li>Groovy script console script execution</li>
     *     <li>{@code groovy} CLI command</li>
     *     <li>Start and end of a {@code groovysh} CLI command session, as well as individual commands submitted</li>
     *     <li>Execution of scripts integrating with Script Security Plugin</li>
     * </ul>
     *
     * @param script The script to be executed or {@code null} if no script is available yet (e.g. a shell has just been opened).
     * @param binding The script binding.
     * @param context Object representing the script definition context (e.g. {@link hudson.model.Run}).
     *                Can be {@code null} if not applicable (e.g., CLI commands not acting on jobs/builds).
     * @param correlationId This value is used to correlate this script event to other, related script events.
     *                      Callers are expected to provide values that allow receivers to associate script definition
     *                      (if applicable), execution and output. Related events should have identical values.
     * @param user If available, the user who executed the script. Can be {@code null}.
     */
    default void onScriptExecution(@CheckForNull String script, @CheckForNull Binding binding, @CheckForNull Object context, @NonNull String correlationId, @CheckForNull User user) {
    }

    /**
     * Called when a script produces output. This can include error output.
     *
     * @param output The output of the script.
     * @param context Object representing the script definition context (e.g. {@link hudson.model.Run}).
     *                Can be {@code null} if not applicable (e.g., CLI commands not acting on jobs/builds).
     * @param correlationId This value is used to correlate this script event to other, related script events.
     *                      Callers are expected to provide values that allow receivers to associate script definition
     *                      (if applicable), execution and output. Related events should have identical values.
     * @param user If available, the user for which the output was created. Can be {@code null}.
     */
    default void onScriptOutput(@CheckForNull String output, @CheckForNull Object context, @NonNull String correlationId, @CheckForNull User user) {
    }

    /**
     * Fires the {@link #onScriptDefinition(String, Object, String, hudson.model.User)} event.
     *
     * @param script The script.
     * @param context Object representing the script definition context (e.g. {@link hudson.model.Job}).
     *                Can be {@code null} if not applicable (e.g., CLI commands not acting on jobs/builds).
     * @param correlationId This value is used to correlate this script event to other, related script events.
     *                      Callers are expected to provide values that allow receivers to associate script definition
     *                      (if applicable), execution and output. Related events should have identical values.
     * @param user If available, the user who caused this event. Can be {@code null}.
     */
    static void fireScriptDefinition(@NonNull String script, @CheckForNull Object context, @NonNull String correlationId, @CheckForNull User user) {
        Listeners.notify(ScriptListener.class, true, listener -> listener.onScriptDefinition(script, context, correlationId, user));
    }

    /**
     * Fires the {@link #onScriptExecution(String, Binding, Object, String, hudson.model.User)} event.
     *
     * @param script The script to be executed or {@code null} if no script is available yet (e.g. a shell has just been opened).
     * @param context Object representing the script definition context (e.g. {@link hudson.model.Run}).
     *                Can be {@code null} if not applicable (e.g., CLI commands not acting on jobs/builds).
     * @param correlationId This value is used to correlate this script event to other, related script events.
     *                      Callers are expected to provide values that allow receivers to associate script definition
     *                      (if applicable), execution and output. Related events should have identical values.
     * @param u If available, the user who caused this event. Can be {@code null}.
     */
    static void fireScriptExecution(@CheckForNull String script, @CheckForNull Binding binding, @CheckForNull Object context, @NonNull String correlationId, @CheckForNull User user) {
        Listeners.notify(ScriptListener.class, true, listener -> listener.onScriptExecution(script, binding, context, correlationId, user));
    }

    /**
     * Fires the {@link #onScriptOutput(String, Object, String, hudson.model.User)} event.
     *
     * @param output The output of the script.
     * @param context Object representing the script definition context (e.g. {@link hudson.model.Run}).
     *                Can be {@code null} if not applicable (e.g., CLI commands not acting on jobs/builds).
     * @param correlationId This value is used to correlate this script event to other, related script events.
     *                      Callers are expected to provide values that allow receivers to associate script definition
     *                      (if applicable), execution and output. Related events should have identical values.
     * @param user If available, the user for which the output was created. Can be {@code null}.
     */
    static void fireScriptOutput(@CheckForNull String output, @CheckForNull Object context, @NonNull String correlationId, @CheckForNull User user) {
        Listeners.notify(ScriptListener.class, true, listener -> listener.onScriptOutput(output, context, correlationId, user));
    }

    /**
     * {@link java.io.Writer} that calls {@link #fireScriptOutput(String, Object, String, hudson.model.User)} with the
     * output it writes to the wrapped {@link java.io.Writer}, and otherwise just forwards {@link #flush()} and {@link #close()}.
     */
    class ListenerWriter extends Writer {

        private final Writer writer;
        private final Object context;
        private final String correlationId;
        private final User user;

        public ListenerWriter(Writer writer, Object context, String correlationId, User user) {
            this.writer = writer;
            this.context = context;
            this.correlationId = correlationId;
            this.user = user;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            ScriptListener.fireScriptOutput(String.copyValueOf(cbuf, off, len), context, correlationId, user);
            writer.write(cbuf, off, len);
        }

        @Override
        public void flush() throws IOException {
            writer.flush();
        }

        @Override
        public void close() throws IOException {
            writer.close();
        }
    }

    /**
     * {@link java.io.OutputStream} that calls{@link #fireScriptOutput(String, Object, String, hudson.model.User)} with
     * the output it writes to the wrapped {@link java.io.OutputStream}, and otherwise just forwards {@link #flush()}
     * and {@link #close()}.
     */
    class ListenerOutputStream extends OutputStream {

        private final OutputStream os;
        private final Object context;
        private final String correlationId;
        private final User user;

        public ListenerOutputStream(OutputStream os, Object context, String correlationId, User user) {
            this.os = os;
            this.context = context;
            this.correlationId = correlationId;
            this.user = user;
        }

        @Override
        public void write(int b) throws IOException {
            // Let's hope for verbosity's sake that nobody calls this directly, #write(byte[], int, int) should take care of regular calls.
            ScriptListener.fireScriptOutput(new String(new byte[] { (byte)b }), context, correlationId, user);
            os.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            final String writtenString = new String(b).substring(off, len - off);
            ScriptListener.fireScriptOutput(writtenString, context, correlationId, user);
            os.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            os.flush();
        }

        @Override
        public void close() throws IOException {
            os.close();
        }
    }
}
