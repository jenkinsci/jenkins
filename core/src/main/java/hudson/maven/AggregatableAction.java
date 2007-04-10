package hudson.maven;

import hudson.model.Action;

import java.util.List;
import java.util.Map;

/**
 * Indicates that this {@link Action} for {@link MavenBuild} contributes
 * an "aggregated" action to {@link MavenBuild#getModuleSetBuild()
 * its governing MavenModuleSetBuild}. 
 *
 * @author Kohsuke Kawaguchi
 * @since 1.99
 * @see MavenReporter
 */
public interface AggregatableAction extends Action {
    /**
     * Creates {@link Action} to be contributed to {@link MavenModuleSetBuild}.
     *
     * @param build
     *      {@link MavenModuleSetBuild} for which the aggregated report is
     *      created.
     * @param moduleBuilds
     *      The result of {@link MavenModuleSetBuild#getModuleBuilds()} provided
     *      for convenience and efficiency.
     * @return
     *      null if the reporter provides no such action.
     */
    MavenAggregatedReport createAggregatedAction(
        MavenModuleSetBuild build, Map<MavenModule,List<MavenBuild>> moduleBuilds);
}
