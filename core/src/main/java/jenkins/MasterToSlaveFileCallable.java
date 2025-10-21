package jenkins;

import hudson.FilePath.FileCallable;
import jenkins.agents.ControllerToAgentFileCallable;

/**
 * {@link FileCallable}s that could run on an agent.
 * For new code, implement {@link ControllerToAgentFileCallable}
 * which has the advantage that it can be used on {@code record}s.
 * @since 1.587 / 1.580.1
 * @param <T> the return type
 */
public abstract class MasterToSlaveFileCallable<T> implements ControllerToAgentFileCallable<T> {

    private static final long serialVersionUID = 1L;
}
