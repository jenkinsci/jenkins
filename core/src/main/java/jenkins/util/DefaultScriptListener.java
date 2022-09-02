package jenkins.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.lang.Binding;
import hudson.Extension;
import hudson.model.User;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Basic default implementation of {@link jenkins.util.ScriptListener} that just logs.
 *
 */
@Extension
@Restricted(NoExternalUse.class)
public class DefaultScriptListener implements ScriptListener {
    public static final Logger LOGGER = Logger.getLogger(DefaultScriptListener.class.getName());

    @Override
    public void onScriptExecution(String script, Binding binding, Object context, @NonNull String correlationId, User user) {
        LOGGER.log(Level.FINE, LOGGER.isLoggable(Level.FINEST) ? new Exception() : null, () -> "Execution of script: '" + script + "' with binding: '" + stringifyBinding(binding) + "' in context: '" + context + "' with correlation: '" + correlationId + "' by user: '" + user + "'");
    }

    @Override
    public void onScriptDefinition(@NonNull String script, Object context, @NonNull String correlationId, User user) {
        LOGGER.log(Level.CONFIG, LOGGER.isLoggable(Level.FINEST) ? new Exception() : null, () -> "Definition of script: '" + script + "' in context: '" + context + "' with correlation: '" + correlationId + "' by user: '" + user + "'");
    }

    @Override
    public void onScriptOutput(String output, Object context, @NonNull String correlationId, User user) {
        LOGGER.log(Level.FINER, LOGGER.isLoggable(Level.FINEST) ? new Exception() : null, () -> "Script output: '" + output + "' in context: '" + context + "' with correlation: '" + correlationId + "' for user: '" + user + "'");
    }

    @SuppressWarnings("unchecked")
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
