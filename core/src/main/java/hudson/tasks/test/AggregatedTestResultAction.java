package hudson.tasks.test;

import hudson.maven.MavenBuild;
import hudson.model.AbstractBuild;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AbstractTestResultAction} that aggregates all the test results
 * from the corresponding {@link AbstractBuild}s.
 *
 * <p>
 * (This has nothing to do with {@link AggregatedTestResultPublisher}, unfortunately)
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public abstract class AggregatedTestResultAction extends AbstractTestResultAction {
    private int failCount,skipCount,totalCount;

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
        failCount = skipCount = totalCount = 0;
        this.children.clear();
        for (AbstractTestResultAction tr : children)
            add(tr);
    }

    protected void add(AbstractTestResultAction child) {
        failCount += child.getFailCount();
        skipCount += child.getSkipCount();
        totalCount += child.getTotalCount();
        this.children.add(new Child(getChildName(child),child.owner.number));
    }

    @Exported
    public int getFailCount() {
        return failCount;
    }

    @Override
    @Exported
    public int getSkipCount() {
        return skipCount;
    }

    @Exported
    public int getTotalCount() {
        return totalCount;
    }

    public List<ChildReport> getResult() {
        // I think this is a reasonable default.
        return getChildReports();
    }

    /**
     * Data-binding bean for the remote API.
     */
    @ExportedBean(defaultVisibility=2)
    public static final class ChildReport {
        @Exported
        public final AbstractBuild<?,?> child;
        @Exported
        public final Object result;

        public ChildReport(AbstractBuild<?, ?> child, AbstractTestResultAction result) {
            this.child = child;
            this.result = result.getResult();
        }
    }

    /**
     * Mainly for the remote API. Expose results from children.
     */
    @Exported(inline=true)
    public List<ChildReport> getChildReports() {
        return new AbstractList<ChildReport>() {
            public ChildReport get(int index) {
                return new ChildReport(
                        resolveChild(children.get(index)),
                        getChildReport(children.get(index)));
            }

            public int size() {
                return children.size();
            }
        };
    }

    protected abstract String getChildName(AbstractTestResultAction tr);
    public abstract AbstractBuild<?,?> resolveChild(Child child);

    /**
     * Uses {@link #resolveChild(Child)} and obtain the
     * {@link AbstractTestResultAction} object for the given child.
     */
    protected AbstractTestResultAction getChildReport(Child child) {
        AbstractBuild<?,?> b = resolveChild(child);
        if(b==null) return null;
        return b.getAction(AbstractTestResultAction.class);
    }
}
