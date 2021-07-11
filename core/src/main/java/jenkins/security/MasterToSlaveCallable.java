package jenkins.security;

/**
 * @since 1.587 / 1.580.1
 * @deprecated Use {@link ControllerToAgentCallable}
 */
@Deprecated
public abstract class MasterToSlaveCallable<V, T extends Throwable> extends ControllerToAgentCallable<V, T> {
}
