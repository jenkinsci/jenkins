package hudson.tasks.test;

import hudson.maven.MavenBuild;
import hudson.model.AbstractBuild;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link AbstractTestResultAction} that aggregates all the test results
 * from the corresponding {@link MavenBuild}s.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class AggregatedTestResultAction<T extends AbstractBuild> extends AbstractTestResultAction {
    private int failCount,totalCount;

    /**
     * {@link MavenBuild}s whose test results are used for aggregation.
     */
    public final List<T> children = new ArrayList<T>();

    public AggregatedTestResultAction(AbstractBuild owner, List<T> children) {
        super(owner);

        for (T build : children) {
            AbstractTestResultAction tr = build.getTestResultAction();
            if(tr==null)    continue;

            failCount += tr.getFailCount();
            totalCount += tr.getTotalCount();
            this.children.add(build);
        }
    }

    public int getFailCount() {
        return failCount;
    }

    public int getTotalCount() {
        return totalCount;
    }
}
