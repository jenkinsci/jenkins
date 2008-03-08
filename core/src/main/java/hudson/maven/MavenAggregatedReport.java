package hudson.maven;

import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.tasks.BuildStep;

import java.util.List;
import java.util.Map;

/**
 * {@link Action} to be associated with {@link MavenModuleSetBuild},
 * which usually displays some aspect of the aggregated results
 * of the module builds (such as aggregated test result, aggregated
 * coverage report, etc.)
 *
 * <p>
 * When a module build is completed, {@link MavenBuild#getModuleSetBuild()
 * its governing MavenModuleSetBuild} tries to create an instane of
 * {@link MavenAggregatedReport} from each kind of {@link MavenReporterDescriptor}
 * whose {@link MavenReporter}s are used on module builds.
 *
 * <p>
 * The obtained instance is then persisted with {@link MavenModuleSetBuild}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.99
 * @see AggregatableAction
 */
public interface MavenAggregatedReport extends Action {
    /**
     * Called whenever a new module build is completed, to update the
     * aggregated report. When multiple builds complete simultaneously,
     * Hudson serializes the execution of this method, so this method
     * needs not be concurrency-safe.
     *
     * @param moduleBuilds
     *      Same as <tt>MavenModuleSet.getModuleBuilds()</tt> but provided for convenience and efficiency.
     * @param newBuild
     *      Newly completed build.
     */
    void update(Map<MavenModule,List<MavenBuild>> moduleBuilds, MavenBuild newBuild);

    /**
     * Returns the implementation class of {@link AggregatableAction} that
     * produces this {@link MavenAggregatedReport}. Hudson uses this method
     * to determine which {@link AggregatableAction} is aggregated to
     * which {@link MavenAggregatedReport}.
     */
    Class<? extends AggregatableAction> getIndividualActionType();

    /**
     * Equivalent of {@link BuildStep#getProjectAction(AbstractProject)}
     * for {@link MavenAggregatedReport}.
     */
    Action getProjectAction(MavenModuleSet moduleSet);
}
