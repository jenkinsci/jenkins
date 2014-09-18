package jenkins.security;

import hudson.remoting.Callable;

/**
 * Convenient {@link Callable} with {@link MasterToSlave} to create anonymous Callable class.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.THU
 */
@MasterToSlave
public abstract class MasterToSlaveCallable<V, T extends Throwable> implements Callable<V,T> {
    private static final long serialVersionUID = 1L;
}
