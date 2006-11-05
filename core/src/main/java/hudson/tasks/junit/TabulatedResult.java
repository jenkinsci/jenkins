package hudson.tasks.junit;

import java.util.Collection;

/**
 * Cumulated result of multiple tests.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class TabulatedResult extends TestObject {

    /**
     * Gets the human readable title of this result object.
     */
    public abstract String getTitle();

    /**
     * Gets the total number of passed tests.
     */
    public abstract int getPassCount();

    /**
     * Gets the total number of failed tests.
     */
    public abstract int getFailCount();

    /**
     * Gets the total number of tests.
     */
    public final int getTotalCount() {
        return getPassCount()+getFailCount();
    }

    /**
     * Gets the child test result objects.
     */
    public abstract Collection<?> getChildren();

}
