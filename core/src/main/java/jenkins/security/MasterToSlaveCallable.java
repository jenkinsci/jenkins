package jenkins.security;

import hudson.remoting.Callable;
import jenkins.agents.ControllerToAgentCallable;

/**
 * {@link Callable} meant to be run on agent.
 * For new code, implement {@link ControllerToAgentCallable}
 * which has the advantage that it can be used on {@code record}s.
 * @author Kohsuke Kawaguchi
 * @since 1.587 / 1.580.1
 * @param <V> the return type
 */
public abstract class MasterToSlaveCallable<V, T extends Throwable> implements ControllerToAgentCallable<V, T> {

    private static final long serialVersionUID = 1L;
}
