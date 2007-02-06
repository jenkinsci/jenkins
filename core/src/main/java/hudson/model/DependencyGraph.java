package hudson.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Stack;
import java.util.Map.Entry;

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
 * @author Kohsuke Kawaguchi
 */
public final class DependencyGraph {

    private Map<AbstractProject, List<AbstractProject>> forward = new HashMap<AbstractProject, List<AbstractProject>>();
    private Map<AbstractProject, List<AbstractProject>> backward = new HashMap<AbstractProject, List<AbstractProject>>();

    private boolean built;

    /**
     * Builds the dependency graph.
     */
    public DependencyGraph() {
        for( AbstractProject p : Hudson.getInstance().getAllItems(AbstractProject.class) )
            p.buildDependencyGraph(this);

        forward = finalize(forward);
        backward = finalize(backward);

        built = true;
    }

    /**
     * Special constructor for creating an empty graph
     */
    private DependencyGraph(boolean dummy) {
        forward = backward = Collections.emptyMap();
        built = true;
    }

    /**
     * Gets all the immediate downstream projects (IOW forward edges) of the given project.
     *
     * @return
     *      can be empty but never null.
     */
    public List<AbstractProject> getDownstream(AbstractProject p) {
        return get(forward,p);
    }

    /**
     * Gets all the immediate upstream projects (IOW backward edges) of the given project.
     *
     * @return
     *      can be empty but never null.
     */
    public List<AbstractProject> getUpstream(AbstractProject p) {
        return get(backward,p);
    }

    private List<AbstractProject> get(Map<AbstractProject, List<AbstractProject>> map, AbstractProject src) {
        List<AbstractProject> v = map.get(src);
        if(v!=null) return v;
        else        return Collections.emptyList();
    }

    /**
     * Called during the dependency graph build phase to add a dependency edge.
     */
    public void addDependency(AbstractProject from, AbstractProject to) {
        if(built)
            throw new IllegalStateException();
        add(forward,from,to);
        add(backward,to,from);
    }

    public void addDependency(AbstractProject from, Collection<? extends AbstractProject> to) {
        for (AbstractProject p : to)
            addDependency(from,p);
    }

    public void addDependency(Collection<? extends AbstractProject> from, AbstractProject to) {
        for (AbstractProject p : from)
            addDependency(p,to);
    }

    /**
     * Returns true if a project has a non-direct dependency to another project.
     * <p>
     * A non-direct dependency is a path of dependency "edge"s from the source to the destination,
     * where the length is greater than 1.
     */
    public boolean hasIndirectDependencies(AbstractProject src, AbstractProject dst) {
        Set<AbstractProject> visited = new HashSet<AbstractProject>();
        Stack<AbstractProject> queue = new Stack<AbstractProject>();

        queue.addAll(getDownstream(src));
        queue.remove(dst);

        while(!queue.isEmpty()) {
            AbstractProject p = queue.pop();
            if(p==dst)
                return true;
            if(visited.add(p))
                queue.addAll(getDownstream(p));
        }

        return false;
    }

    /**
     * Gets all the direct and indirect upstream dependencies of the given project.
     */
    public Set<AbstractProject> getTransitiveUpstream(AbstractProject src) {
        return getTransitive(backward,src);
    }

    /**
     * Gets all the direct and indirect downstream dependencies of the given project.
     */
    public Set<AbstractProject> getTransitiveDownstream(AbstractProject src) {
        return getTransitive(forward,src);
    }

    private Set<AbstractProject> getTransitive(Map<AbstractProject, List<AbstractProject>> direction, AbstractProject src) {
        Set<AbstractProject> visited = new HashSet<AbstractProject>();
        Stack<AbstractProject> queue = new Stack<AbstractProject>();

        queue.add(src);

        while(!queue.isEmpty()) {
            AbstractProject p = queue.pop();

            for (AbstractProject child : get(direction,p)) {
                if(visited.add(child))
                    queue.add(child);
            }
        }

        return visited;
    }

    private void add(Map<AbstractProject, List<AbstractProject>> map, AbstractProject src, AbstractProject dst) {
        List<AbstractProject> set = map.get(src);
        if(set==null) {
            set = new ArrayList<AbstractProject>();
            map.put(src,set);
        }
        set.add(dst);
    }

    private Map<AbstractProject, List<AbstractProject>> finalize(Map<AbstractProject, List<AbstractProject>> m) {
        for (Entry<AbstractProject, List<AbstractProject>> e : m.entrySet()) {
            Collections.sort( e.getValue(), NAME_COMPARATOR );
            e.setValue( Collections.unmodifiableList(e.getValue()) );
        }
        return Collections.unmodifiableMap(m);
    }

    private static final Comparator<AbstractProject> NAME_COMPARATOR = new Comparator<AbstractProject>() {
        public int compare(AbstractProject lhs, AbstractProject rhs) {
            return lhs.getName().compareTo(rhs.getName());
        }
    };

    public static final DependencyGraph EMPTY = new DependencyGraph(false);
}
