/*
 * The MIT License
 *
 * Copyright (c) 2026, Tim Jacomb
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

package jenkins.model;

import com.google.common.collect.Iterables;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.util.RunList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import jenkins.widgets.RunListData;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;

/**
 * Supplies the runs that used a {@link Node}, as listed by {@link Computer#getBuilds()} and its build history page.
 * <p>
 * Core covers {@link AbstractBuild}s through {@link AbstractBuild#getBuiltOn()}.
 * Other kinds of run, such as Pipeline runs that use several nodes, can be added by plugins.
 * Each run should be supplied by only one provider.
 *
 * @since TODO
 */
public abstract class NodeBuildsProvider implements ExtensionPoint {

    /**
     * Lists the runs that may have used a node.
     * <p>
     * The runs must be ordered newest first by {@link Run#getTimeInMillis()},
     * as {@link RunList} orders them, since the build history page pages through them by that time.
     * The list may include runs that did not use the node, which {@link #usedNode} then rejects;
     * this keeps any expensive check out of the iteration so that a page can stop after a time limit.
     * The list should be lazy, as typically only its first runs are read.
     *
     * @param node the node
     * @return candidate runs, newest first, readable by the current user
     */
    @NonNull
    public abstract Iterable<? extends Run<?, ?>> getCandidates(@NonNull Node node);

    /**
     * Checks whether a run from {@link #getCandidates} used a node.
     *
     * @param run a run from {@link #getCandidates}
     * @param node the node
     * @return true to list the run
     */
    public abstract boolean usedNode(@NonNull Run<?, ?> run, @NonNull Node node);

    public static ExtensionList<NodeBuildsProvider> all() {
        return ExtensionList.lookup(NodeBuildsProvider.class);
    }

    /**
     * Lists the runs that used a node, from all providers, newest first.
     */
    @Restricted(NoExternalUse.class)
    public static RunList<Run<?, ?>> builds(@NonNull Node node) {
        return RunList.fromIterable(Iterables.transform(Iterables.filter(candidates(node), Candidate::usedNode), Candidate::run));
    }

    /**
     * Serves a page of {@link #builds} as JSON.
     */
    @Restricted(NoExternalUse.class)
    public static HttpResponse serve(@NonNull RunListData data, @NonNull Node node, String olderThan) {
        return data.serve(candidates(node), Candidate::run, Candidate::usedNode, olderThan);
    }

    private static Iterable<Candidate> candidates(Node node) {
        List<Iterable<Candidate>> lists = new ArrayList<>();
        for (NodeBuildsProvider provider : all()) {
            lists.add(Iterables.transform(provider.getCandidates(node), run -> new Candidate(provider, run, node)));
        }
        return Iterables.mergeSorted(lists, Comparator.comparingLong((Candidate candidate) -> candidate.run.getTimeInMillis()).reversed());
    }

    private record Candidate(NodeBuildsProvider provider, Run<?, ?> run, Node node) {
        boolean usedNode() {
            return provider.usedNode(run, node);
        }
    }

    /**
     * Lists {@link AbstractBuild}s by {@link AbstractBuild#getBuiltOn()}.
     */
    @Restricted(NoExternalUse.class)
    @Extension
    public static final class AbstractBuilds extends NodeBuildsProvider {

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public Iterable<? extends Run<?, ?>> getCandidates(@NonNull Node node) {
            return RunList.fromJobs((Iterable) Jenkins.get().allItems(AbstractProject.class));
        }

        @Override
        public boolean usedNode(@NonNull Run<?, ?> run, @NonNull Node node) {
            return run instanceof AbstractBuild && ((AbstractBuild<?, ?>) run).getBuiltOn() == node;
        }
    }
}
