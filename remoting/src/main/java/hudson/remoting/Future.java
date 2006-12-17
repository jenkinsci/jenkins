package hudson.remoting;

/**
 * Alias to {@link Future}.
 *
 * <p>
 * This alias is defined so that retro-translation won't affect
 * the publicly committed signature of the API.
 * 
 * @author Kohsuke Kawaguchi
 */
public interface Future<V> extends java.util.concurrent.Future<V> {
}
