package hudson.remoting;

/**
 * {@link Callable} that nominates another claassloader for serialization.
 *
 * <p>
 * For various reasons, one {@link Callable} object is serialized by one classloader.
 * Normally the classloader that loaded {@link Callable} implementation will be used,
 * but when {@link Callable} further delegates to another classloader, that might
 * not be suitable. Implementing this interface allows {@link Callable} to
 * use designate classloader.
 *
 * @author Kohsuke Kawaguchi
 */
public interface DelegatingCallable<V,T extends Throwable> extends Callable<V,T> {
    ClassLoader getClassLoader();
}
