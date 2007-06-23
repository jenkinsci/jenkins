package hudson.util;

/**
 * Unary function <tt>y=f(x)</tt>.
 * 
 * @author Kohsuke Kawaguchi
 */
public interface Function1<R,P1> {
    R call(P1 param1);
}
