package hudson.tasks.junit;

import java.util.Collection;
import java.util.List;

/**
 * {@link TabulatedResult} whose immediate children
 * are other {@link TabulatedResult}s.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class MetaTabulatedResult extends TabulatedResult {
    public abstract String getChildTitle();

    /**
     * All failed tests.
     */
    public abstract List<CaseResult> getFailedTests();

    public abstract Collection<? extends TabulatedResult> getChildren();
}
