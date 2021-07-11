package jenkins.security;

/**
 * @deprecated Use {@link AgentToControllerCallable}
 * @since 1.587 / 1.580.1
 */
@Deprecated
public abstract class SlaveToMasterCallable<V, T extends Throwable> extends AgentToControllerCallable<V, T> {
}
