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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.lang.Binding;
import hudson.ExtensionPoint;
import hudson.model.User;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * A listener to track in-process script use.
 *
 * <p>Note that (unsandboxed) script execution can easily result in logging configuration being changed, so if you rely
 * on complete logging of scripting actions, make sure to set up logging to remote systems.</p>
 *
 * @see jenkins.model.Jenkins#_doScript(StaplerRequest2, org.kohsuke.stapler.StaplerResponse2, jakarta.servlet.RequestDispatcher, hudson.remoting.VirtualChannel, hudson.security.ACL)
 * @see hudson.cli.GroovyCommand
 * @see hudson.cli.GroovyshCommand
 * @see jenkins.util.groovy.GroovyHookScript
 *
 * @since 2.427
 */
public interface ScriptListener extends ExtensionPoint {

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
     * @param binding The script binding, or {@code null} if unavailable/inapplicable.
     * @param feature The feature that triggered this event. Usually a fixed string or even a {@link java.lang.Class}
     *                if that's unambiguously describing the feature (e.g., {@link hudson.cli.GroovyshCommand#getClass()}).
     * @param context Object representing the script definition context (e.g. {@link hudson.model.Run}).
     *                Can be {@code null} if not applicable (e.g., CLI commands not acting on jobs/builds).
     * @param correlationId This value is used to correlate this script event to other, related script events.
     *                      Callers are expected to provide values that allow receivers to associate script execution
     *                      and output. Related events should have identical values.
     * @param user If available, the user who executed the script. Can be {@code null}.
     */
    default void onScriptExecution(@CheckForNull String script, @CheckForNull Binding binding, @NonNull Object feature, @CheckForNull Object context, @NonNull String correlationId, @CheckForNull User user) {
    }

    /**
     * Called when a script produces output. This can include error output.
     *
     * @param output The output of the script.
     * @param feature The feature that triggered this event. Usually a fixed string or even a {@link java.lang.Class}
     *                if that's unambiguously describing the feature (e.g., {@link hudson.cli.GroovyshCommand#getClass()}).
     * @param context Object representing the script definition context (e.g. {@link hudson.model.Run}).
     *                Can be {@code null} if not applicable (e.g., CLI commands not acting on jobs/builds).
     * @param correlationId This value is used to correlate this script event to other, related script events.
     *                      Callers are expected to provide values that allow receivers to associate script execution
     *                      and output. Related events should have identical values.
     * @param user If available, the user for which the output was created. Can be {@code null}.
     */
    default void onScriptOutput(@CheckForNull String output, @NonNull Object feature, @CheckForNull Object context, @NonNull String correlationId, @CheckForNull User user) {
    }

    /**
     * Fires the {@link #onScriptExecution(String, Binding, Object, Object, String, hudson.model.User)} event.
     *
     * @param script The script to be executed or {@code null} if no script is available yet (e.g. a shell has just been opened).
     * @param binding The script binding, or {@code null} if unavailable/inapplicable.
     * @param feature The feature that triggered this event. Usually a fixed string or even a {@link java.lang.Class}
     *                if that's unambiguously describing the feature (e.g., {@link hudson.cli.GroovyshCommand#getClass()}).
     * @param context Object representing the script definition context (e.g. {@link hudson.model.Run}).
     *                Can be {@code null} if not applicable (e.g., CLI commands not acting on jobs/builds).
     * @param correlationId This value is used to correlate this script event to other, related script events.
     *                      Callers are expected to provide values that allow receivers to associate script execution
     *                      and output. Related events should have identical values.
     * @param user If available, the user who caused this event. Can be {@code null}.
     */
    // TODO Should null script be allowed? Do we care about e.g. someone starting groovysh but not actually executing a command (yet)?
    static void fireScriptExecution(@CheckForNull String script, @CheckForNull Binding binding, @NonNull Object feature, @CheckForNull Object context, @NonNull String correlationId, @CheckForNull User user) {
        Listeners.notify(ScriptListener.class, true, listener -> listener.onScriptExecution(script, binding, feature, context, correlationId, user));
    }

    /**
     * Fires the {@link #onScriptOutput(String, Object, Object, String, hudson.model.User)} event.
     *
     * @param output The output of the script.
     * @param context Object representing the script definition context (e.g. {@link hudson.model.Run}).
     *                Can be {@code null} if not applicable (e.g., CLI commands not acting on jobs/builds).
     * @param correlationId This value is used to correlate this script event to other, related script events.
     *                      Callers are expected to provide values that allow receivers to associate script execution
     *                      and output. Related events should have identical values.
     * @param user If available, the user for which the output was created. Can be {@code null}.
     */
    static void fireScriptOutput(@CheckForNull String output, @NonNull Object feature, @CheckForNull Object context, @NonNull String correlationId, @CheckForNull User user) {
        Listeners.notify(ScriptListener.class, true, listener -> listener.onScriptOutput(output, feature, context, correlationId, user));
    }

    /**
     * {@link java.io.Writer} that calls {@link #fireScriptOutput(String, Object, Object, String, hudson.model.User)} with the
     * output it writes to the wrapped {@link java.io.Writer}, and otherwise just forwards {@link #flush()} and {@link #close()}.
     */
    @Restricted(NoExternalUse.class)
    class ListenerWriter extends Writer {

        private final Writer writer;
        private final Object feature;
        private final Object context;
        private final String correlationId;
        private final User user;

        @SuppressFBWarnings("EI_EXPOSE_REP2")
        public ListenerWriter(Writer writer, Object feature, Object context, String correlationId, User user) {
            this.writer = writer;
            this.feature = feature;
            this.context = context;
            this.correlationId = correlationId;
            this.user = user;
        }

        @Override
        public void write(@NonNull char[] cbuf, int off, int len) throws IOException {
            ScriptListener.fireScriptOutput(String.copyValueOf(cbuf, off, len), feature, context, correlationId, user);
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
     * {@link java.io.OutputStream} that calls{@link #fireScriptOutput(String, Object, Object, String, hudson.model.User)} with
     * the output it writes to the wrapped {@link java.io.OutputStream}, and otherwise just forwards {@link #flush()}
     * and {@link #close()}.
     */
    @Restricted(NoExternalUse.class)
    class ListenerOutputStream extends OutputStream {

        private final OutputStream os;
        private final Charset charset;
        private final Object feature;
        private final Object context;
        private final String correlationId;
        private final User user;

        @SuppressFBWarnings("EI_EXPOSE_REP2")
        public ListenerOutputStream(OutputStream os, Charset charset, Object feature, Object context, String correlationId, User user) {
            this.os = os;
            this.charset = charset;
            this.feature = feature;
            this.context = context;
            this.correlationId = correlationId;
            this.user = user;
        }

        @Override
        public void write(int b) throws IOException {
            // Let's hope for verbosity's sake that nobody calls this directly, #write(byte[], int, int) should take care of regular calls.
            ScriptListener.fireScriptOutput(new String(new byte[] { (byte) b }, charset), feature, context, correlationId, user);
            os.write(b);
        }

        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            final String writtenString = new String(b, charset).substring(off, len - off);
            ScriptListener.fireScriptOutput(writtenString, feature, context, correlationId, user);
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
