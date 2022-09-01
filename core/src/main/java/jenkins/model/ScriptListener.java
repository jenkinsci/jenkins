package jenkins.model;

import groovy.lang.Binding;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.User;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.util.Listeners;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;

/**
 * A listener to track Groovy scripts.
 *
 * @see Jenkins#_doScript(StaplerRequest, org.kohsuke.stapler.StaplerResponse, javax.servlet.RequestDispatcher, hudson.remoting.VirtualChannel, hudson.security.ACL)
 * @see hudson.cli.GroovyCommand#run()
 * @see hudson.cli.GroovyshCommand#run()
 */
public interface ScriptListener extends ExtensionPoint {

    enum Usage {
        DEFINITION,
        EXECUTION,
        OTHER
    }

    /**
     * Called just before script-related events. Examples include:
     * <ul>
     *     <li>Groovy script console script execution</li>
     *     <li>Groovy CLI command</li>
     *     <li>Start and end of a Groovysh CLI command session, as well as individual commands submitted</li>
     *     <li>Definition and execution (as separate events) of scripts integrating with Script Security Plugin</li>
     * </ul>
     *
     * @see Jenkins#_doScript(StaplerRequest, org.kohsuke.stapler.StaplerResponse, javax.servlet.RequestDispatcher, hudson.remoting.VirtualChannel, hudson.security.ACL)
     * @param script The script to be executed.
     * @param usage The type of script usage event.
     * @param context Object representing the script execution context (e.g. {@link hudson.model.Run}), or {@code null} if not applicable (e.g. CLI commands not acting on jobs/builds).
     * @param description Descriptive identifier of the origin where the script is executed. This value should be chosen to allow sorting/groyping by this value.
     * @param u If available, the user that executed the script. Can be null.
     */
    void onScriptEvent(String script, Binding binding, Usage usage, Object context, String description, User u);

    /**
     * Fires the {@link #onScriptEvent(String, Binding, jenkins.model.ScriptListener.Usage, Object, String, hudson.model.User)} event to track the usage of the script console.
     *
     * @see Jenkins#_doScript(StaplerRequest, org.kohsuke.stapler.StaplerResponse, javax.servlet.RequestDispatcher, hudson.remoting.VirtualChannel, hudson.security.ACL)
     * @param script The script to be executed or {@code null} if the script event does not involve a script.
     * @param binding The script binding or {@code null} if the script event does not involve a binding. Listeners are expected to not modify the binding.
     * @param usage The type of script usage event.
     * @param context Object representing the script execution context (e.g. {@link hudson.model.Run}), or {@code null} if not applicable (e.g. CLI commands not acting on jobs/builds).
     * @param description Descriptive identifier of the origin where the script is executed. This value should be chosen to allow sorting/groyping by this value.
     * @param u If available, the user that executed the script.
     */
    static void fireScriptEvent(String script, Binding binding, Usage usage, Object context, String description, User u) {
        Listeners.notify(ScriptListener.class, true, listener -> listener.onScriptEvent(script, binding, usage, context, description, u));
    }

    @Extension
    @Restricted(NoExternalUse.class)
    class LoggingListener implements ScriptListener {
        public static final Logger LOGGER = Logger.getLogger(LoggingListener.class.getName());

        @Override
        public void onScriptEvent(String script, Binding binding, Usage usage, Object context, String description, User u) {
            // Awkward to change verbosity at FINE depending on loggability of FINEST, but JENKINS-69496
            LOGGER.log(Level.FINE, LOGGER.isLoggable(Level.FINEST) ? new Exception() : null, () -> "Script: '" + script + "' with binding: '" + stringifyBinding(binding) + "' with usage: '" + usage + "' in context: '" + context + "' with description: '" + description + "' by user: '" + u + "'");
        }

        private static String stringifyBinding(Binding binding) {
            if (binding == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("[");

            sb.append(binding.getVariables().entrySet().stream().map(o -> {
                if (o instanceof Map.Entry) {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
                    return entry.getKey() + ":" + entry.getValue();
                } else {
                    return o.toString();
                }
            }).collect(Collectors.joining(",")));

            sb.append("]");

            return sb.toString();
        }
    }

    class LoggingWriter extends Writer {

        private final Writer writer;
        private final Object context;
        private final String description;

        public LoggingWriter(Writer writer, Object context, String description) {
            this.writer = writer;
            this.context = context;
            this.description = description;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            ScriptListener.fireScriptEvent(String.copyValueOf(cbuf, off, len), null, Usage.OTHER, context, description, User.current());
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

    class LoggingOutputStream extends OutputStream {

        private final OutputStream os;
        private final Object context;
        private final String description;

        public LoggingOutputStream(OutputStream os, Object context, String description) {
            this.os = os;
            this.context = context;
            this.description = description;
        }

        @Override
        public void write(int b) throws IOException {
            // Hopefully this never occurs
            ScriptListener.fireScriptEvent(new String(new byte[] { (byte)b }), null, Usage.OTHER, context, description, User.current());
            os.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            final String writtenString = new String(b).substring(off, len - off);
            ScriptListener.fireScriptEvent(writtenString, null, Usage.OTHER, context, description, User.current());
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
