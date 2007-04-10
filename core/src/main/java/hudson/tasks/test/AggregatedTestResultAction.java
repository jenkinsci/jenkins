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
public abstract class AggregatedTestResultAction extends AbstractTestResultAction {
    private int failCount,totalCount;

    public static final class Child {
        /**
         * Name of the module. Could be relative to something.
         * The interpretation of this is done by
         * {@link AggregatedTestResultAction#getChildName(AbstractTestResultAction)} and
         * {@link AggregatedTestResultAction#resolveChild(Child)} and
         */
        public final String name;
        public final int build;

        public Child(String name, int build) {
            this.name = name;
            this.build = build;
        }
    }

    /**
     * {@link MavenBuild}s whose test results are used for aggregation.
     */
    public final List<Child> children = new ArrayList<Child>();

    public AggregatedTestResultAction(AbstractBuild owner) {
        super(owner);
    }

    protected void update(List<? extends AbstractTestResultAction> children) {
        failCount = totalCount = 0;
        children.clear();
        for (AbstractTestResultAction tr : children) {
            failCount += tr.getFailCount();
            totalCount += tr.getTotalCount();
            this.children.add(new Child(getChildName(tr),tr.owner.number));
        }
    }

    public int getFailCount() {
        return failCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    protected abstract String getChildName(AbstractTestResultAction tr);
    protected abstract AbstractBuild<?,?> resolveChild(Child child);
}
