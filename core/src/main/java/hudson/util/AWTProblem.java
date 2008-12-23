package hudson.util;

/**
 * @author Kohsuke Kawaguchi
 */
public class AWTProblem extends ErrorObject {
    public final Throwable cause;

    public AWTProblem(Throwable cause) {
        this.cause = cause;
    }
}

