package hudson.tasks.test;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.matrix.MatrixBuild;
import hudson.matrix.Combination;
import hudson.matrix.MatrixRun;

/**
 * {@link Action} that aggregates all the test results from {@link MatrixRun}s.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixTestResult extends AggregatedTestResultAction {
    public MatrixTestResult(MatrixBuild owner) {
        super(owner);
    }

    /**
     * Use the configuration name.
     */
    @Override
    protected String getChildName(AbstractTestResultAction tr) {
        return tr.owner.getProject().getName();
    }

    @Override
    public AbstractBuild<?,?> resolveChild(Child child) {
        MatrixBuild b = (MatrixBuild)owner;
        return b.getRun(Combination.fromString(child.name));
    }
}
