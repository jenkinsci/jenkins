package jenkins.security;

import hudson.remoting.Callable;

/**
 * Convenient {@link Callable} with {@link SlaveToMaster} to create anonymous Callable class.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.THU
 */
@SlaveToMaster
public abstract class SlaveToMasterCallable<V, T extends Throwable> implements Callable<V,T> {
    private static final long serialVersionUID = 1L;
}
