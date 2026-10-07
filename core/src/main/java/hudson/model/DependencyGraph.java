/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Martin Eigenbrodt. Seiji Sogabe, Alan Harder
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

package hudson.model;

import hudson.security.ACL;
import hudson.security.ACLContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import jenkins.model.DependencyDeclarer;
import jenkins.model.Jenkins;
import jenkins.util.DirectedGraph;
import jenkins.util.DirectedGraph.SCC;

/**
 * Maintains the build dependencies between {@link AbstractProject}s
 * for efficient dependency computation.
 *
 * <p>
 * The "master" data of dependencies are owned/persisted/maintained by
 * individual {@link AbstractProject}s, but because of that, it's relatively
 * slow  to compute backward edges.
 *
 * <p>
 * This class builds the complete bi-directional dependency graph
 * by collecting information from all {@link AbstractProject}s.
 *
 * <p>
 * Once built, {@link DependencyGraph} is immutable, and every time
 * there's a change (which is relatively rare), a new instance
 * will be created. This eliminates the need of synchronization.
 *
 * @see Jenkins#getDependencyGraph()
 * @author Kohsuke Kawaguchi
 */
public class DependencyGraph implements Comparator<AbstractProject> {

    private Map<AbstractProject, List<DependencyGroup>> forward = new HashMap<>();
    private Map<AbstractProject, List<DependencyGroup>> backward = new HashMap<>();

    private transient Map<Class<?>, Object> computationalData;

    private boolean built;

    private Comparator<AbstractProject<?, ?>> topologicalOrder;
    private List<AbstractProject<?, ?>> topologicallySorted;

    /**
     * Builds the dependency graph.
     */
    public DependencyGraph() {
    }

    public void build() {
        // Set full privileges while computing to avoid missing any projects the current user cannot see.
        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            this.computationalData = new HashMap<>();
            for (AbstractProject p : Jenkins.get().allItems(AbstractProject.class))
                p.buildDependencyGraph(this);

            forward = finalize(forward);
            backward = finalize(backward);
            topologicalDagSort();
            this.computationalData = null;
            built = true;
        }
    }

    /**
     *
     *
     * See <a href="https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm">Tarjan's strongly connected components algorithm</a>
     */
    private void topologicalDagSort() {
        DirectedGraph<AbstractProject> g = new DirectedGraph<>() {
            @Override
            protected Collection<AbstractProject> nodes() {
                final Set<AbstractProject> nodes = new HashSet<>();
                nodes.addAll(forward.keySet());
                nodes.addAll(backward.keySet());
                return nodes;
            }

            @Override
            protected Collection<AbstractProject> forward(AbstractProject node) {
                return getDownstream(node);
            }
        };

        List<SCC<AbstractProject>> sccs = g.getStronglyConnectedComponents();

        final Map<AbstractProject, Integer> topoOrder = new HashMap<>();
        topologicallySorted = new ArrayList<>();
        int idx = 0;
        for (SCC<AbstractProject> scc : sccs) {
            for (AbstractProject n : scc) {
                topoOrder.put(n, idx++);
                topologicallySorted.add(n);
            }
        }

        topologicalOrder = Comparator.comparingInt(topoOrder::get);

        topologicallySorted = Collections.unmodifiableList(topologicallySorted);
    }

    /**
     * Special constructor for creating an empty graph
     */
    private DependencyGraph(boolean dummy) {
        forward = backward = Collections.emptyMap();
        topologicalDagSort();
        built = true;
    }

    /**
     * Adds data which is useful for the time when the dependency graph is built up.
     * All this data will be cleaned once the dependency graph creation has finished.
     */
    public <T> void putComputationalData(Class<T> key, T value) {
        this.computationalData.put(key, value);
    }

    /**
     * Gets temporary data which is needed for building up the dependency graph.
     */
    public <T> T getComputationalData(Class<T> key) {
        @SuppressWarnings("unchecked")
        T result = (T) this.computationalData.get(key);
        return result;
    }

    /**
     * Gets all the immediate downstream projects (IOW forward edges) of the given project.
     *
     * @return
     *      can be empty but never null.
     */
    public List<AbstractProject> getDownstream(AbstractProject p) {
        return get(forward, p, false);
    }

    /**
     * Gets all the immediate upstream projects (IOW backward edges) of the given project.
     *
     * @return
     *      can be empty but never null.
     */
    public List<AbstractProject> getUpstream(AbstractProject p) {
        return get(backward, p, true);
    }

    private List<AbstractProject> get(Map<AbstractProject, List<DependencyGroup>> map, AbstractProject src, boolean up) {
        List<DependencyGroup> v = map.get(src);
        if (v == null) return Collections.emptyList();
        List<AbstractProject> result = new ArrayList<>(v.size());
        for (DependencyGroup d : v) result.add(up ? d.getUpstreamProject() : d.getDownstreamProject());
        return result;
    }

    /**
     * @since 1.341
     */
    public List<Dependency> getDownstreamDependencies(AbstractProject p) {
        return get(forward, p);
    }

    /**
     * @since 1.341
     */
    public List<Dependency> getUpstreamDependencies(AbstractProject p) {
        return get(backward, p);
    }

    private List<Dependency> get(Map<AbstractProject, List<DependencyGroup>> map, AbstractProject src) {
        List<DependencyGroup> v = map.get(src);
        if (v == null) {
            return Collections.emptyList();
        } else {
            List<Dependency> builder = new ArrayList<>();
            for (DependencyGroup dependencyGroup : v) {
                builder.addAll(dependencyGroup.getGroup());
            }
            return Collections.unmodifiableList(builder);
        }

    }

    /**
     * @deprecated since 1.341; use {@link #addDependency(Dependency)}
     */
    @Deprecated
    public void addDependency(AbstractProject upstream, AbstractProject downstream) {
        addDependency(new Dependency(upstream, downstream));
    }

    /**
     * Called during the dependency graph build phase to add a dependency edge.
     */
    public void addDependency(Dependency dep) {
        if (built)
            throw new IllegalStateException();
        add(forward, dep.getUpstreamProject(), dep);
        add(backward, dep.getDownstreamProject(), dep);
    }

    /**
     * @deprecated since 1.341
     */
    @Deprecated
    public void addDependency(AbstractProject upstream, Collection<? extends AbstractProject> downstream) {
        for (AbstractProject p : downstream)
            addDependency(upstream, p);
    }

    /**
     * @deprecated since 1.341
     */
    @Deprecated
    public void addDependency(Collection<? extends AbstractProject> upstream, AbstractProject downstream) {
        for (AbstractProject p : upstream)
            addDependency(p, downstream);
    }

    /**
     * Lists up {@link DependencyDeclarer} from the collection and let them builds dependencies.
     */
    public void addDependencyDeclarers(AbstractProject upstream, Collection<?> possibleDependecyDeclarers) {
        for (Object o : possibleDependecyDeclarers) {
            if (o instanceof DependencyDeclarer dd) {
                dd.buildDependencyGraph(upstream, this);
            }
        }
    }

    /**
     * Returns true if a project has a non-direct dependency to another project.
     * <p>
     * A non-direct dependency is a path of dependency "edge"s from the source to the destination,
     * where the length is greater than 1.
     */
    public boolean hasIndirectDependencies(AbstractProject src, AbstractProject dst) {
        Set<AbstractProject> visited = new HashSet<>();
        Stack<AbstractProject> queue = new Stack<>();

        queue.addAll(getDownstream(src));
        queue.remove(dst);

        while (!queue.isEmpty()) {
            AbstractProject p = queue.pop();
            if (p == dst)
                return true;
            if (visited.add(p))
                queue.addAll(getDownstream(p));
        }

        return false;
    }

    /**
     * Gets all the direct and indirect upstream dependencies of the given project.
     */
    public Set<AbstractProject> getTransitiveUpstream(AbstractProject src) {
        return getTransitive(backward, src, true);
    }

    /**
     * Gets all the direct and indirect downstream dependencies of the given project.
     */
    public Set<AbstractProject> getTransitiveDownstream(AbstractProject src) {
        return getTransitive(forward, src, false);
    }

    private Set<AbstractProject> getTransitive(Map<AbstractProject, List<DependencyGroup>> direction, AbstractProject src, boolean up) {
        Set<AbstractProject> visited = new HashSet<>();
        Stack<AbstractProject> queue = new Stack<>();

        queue.add(src);

        while (!queue.isEmpty()) {
            AbstractProject p = queue.pop();

            for (AbstractProject child : get(direction, p, up)) {
                if (visited.add(child))
                    queue.add(child);
            }
        }

        return visited;
    }

    private void add(Map<AbstractProject, List<DependencyGroup>> map, AbstractProject key, Dependency dep) {
        List<DependencyGroup> set = map.computeIfAbsent(key, k -> new ArrayList<>());
        for (DependencyGroup d : set) {
            // Check for existing edge that connects the same two projects:
            if (d.getUpstreamProject() == dep.getUpstreamProject() && d.getDownstreamProject() == dep.getDownstreamProject()) {
                d.add(dep);
                return;
            }
        }
        // Otherwise add to list:
        set.add(new DependencyGroup(dep));
    }

    private Map<AbstractProject, List<DependencyGroup>> finalize(Map<AbstractProject, List<DependencyGroup>> m) {
        for (Map.Entry<AbstractProject, List<DependencyGroup>> e : m.entrySet()) {
            e.getValue().sort(NAME_COMPARATOR);
            e.setValue(Collections.unmodifiableList(e.getValue()));
        }
        return Collections.unmodifiableMap(m);
    }

    private static final Comparator<DependencyGroup> NAME_COMPARATOR = Comparator.comparing((DependencyGroup lhs) -> lhs.getUpstreamProject().getName()).thenComparing(lhs -> lhs.getDownstreamProject().getName());

    public static final DependencyGraph EMPTY = new DependencyGraph(false);

    /**
     * Compare two Projects based on the topological order defined by this Dependency Graph
     */
    @Override
    public int compare(AbstractProject o1, AbstractProject o2) {
        return topologicalOrder.compare(o1, o2);
    }

    /**
     * Returns all the projects in the topological order of the dependency.
     *
     * Intuitively speaking, the first one in the list is the source of the dependency graph,
     * and the last one is the sink.
     *
     * @since 1.521
     */
    public List<AbstractProject<?, ?>> getTopologicallySorted() {
        return topologicallySorted;
    }

    /**
     * Represents an edge in the dependency graph.
     * @since 1.341
     */
    public static class Dependency {
        private AbstractProject upstream, downstream;

        public Dependency(AbstractProject upstream, AbstractProject downstream) {
            this.upstream = upstream;
            this.downstream = downstream;
        }

        public AbstractProject getUpstreamProject() {
            return upstream;
        }

        public AbstractProject getDownstreamProject() {
            return downstream;
        }

        /**
         * Decide whether build should be triggered and provide any Actions for the build.
         * Default implementation always returns true (for backward compatibility), and
         * adds no Actions. Subclasses may override to control how/if the build is triggered.
         * <p>The authentication in effect ({@link Jenkins#getAuthentication2}) will be that of the upstream build.
         * An implementation is expected to perform any relevant access control checks:
         * that an upstream project can both see and build a downstream project,
         * or that a downstream project can see an upstream project.
         * @param build Build of upstream project that just completed
         * @param listener For any error/log output
         * @param actions Add Actions for the triggered build to this list; never null
         * @return True to trigger a build of the downstream project
         */
        public boolean shouldTriggerBuild(AbstractBuild build, TaskListener listener,
                                          List<Action> actions) {
            return true;
        }

        /**
         * Does this method point to itself?
         */
        public boolean pointsItself() {
            return upstream == downstream;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;

            final Dependency that = (Dependency) obj;
            return this.upstream == that.upstream || this.downstream == that.downstream;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 23 * hash + this.upstream.hashCode();
            hash = 23 * hash + this.downstream.hashCode();
            return hash;
        }

        @Override public String toString() {
            return super.toString() + "[" + upstream + "->" + downstream + "]";
        }
    }

    /**
     * Collect multiple dependencies between the same two projects.
     */
    private static class DependencyGroup {
        private Set<Dependency> group = new LinkedHashSet<>();

        DependencyGroup(Dependency first) {
            this.upstream = first.getUpstreamProject();
            this.downstream = first.getDownstreamProject();
            group.add(first);
        }

        private void add(Dependency next) {
            group.add(next);
        }

        public Set<Dependency> getGroup() {
            return group;
        }

        private AbstractProject upstream, downstream;

        public AbstractProject getUpstreamProject() {
            return upstream;
        }

        public AbstractProject getDownstreamProject() {
            return downstream;
        }
    }
}
