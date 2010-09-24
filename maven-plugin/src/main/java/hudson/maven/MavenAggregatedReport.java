/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.maven;

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
