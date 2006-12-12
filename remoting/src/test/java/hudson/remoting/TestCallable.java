package hudson.remoting;

/**
 * {@link Callable} used to verify the classloader used.
 *
 * @author Kohsuke Kawaguchi
 */
public class TestCallable implements Callable {
    public Object call() {
        return getClass().getClassLoader().toString();
    }
}
