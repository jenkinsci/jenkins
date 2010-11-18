package hudson.util;

import hudson.Util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * Traverses a directed graph and if it contains any cycle, throw an exception.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class CyclicGraphDetector<N> {
    private final Set<N> visited = new HashSet<N>();
    private final Set<N> visiting = new HashSet<N>();
    private final Stack<N> path = new Stack<N>();

    public void run(Iterable<? extends N> allNodes) throws CycleDetectedException {
        for (N n : allNodes)
            visit(n);
    }

    /**
     * List up edges from the given node (by listing nodes that those edges point to.)
     *
     * @return
     *      Never null.
     */
    protected abstract Iterable<? extends N> getEdges(N n);

    private void visit(N p) throws CycleDetectedException {
        if (!visited.add(p))    return;

        visiting.add(p);
        path.push(p);
        for (N q : getEdges(p)) {
            if (q==null)        continue;   // ignore unresolved references
            if (visiting.contains(q))
                detectedCycle(q);
            visit(q);
        }
        visiting.remove(p);
        path.pop();
    }

    private void detectedCycle(N q) throws CycleDetectedException {
        int i = path.indexOf(q);
        path.push(q);
        throw new CycleDetectedException(path.subList(i, path.size()));
    }

    public static final class CycleDetectedException extends Exception {
        public final List cycle;

        public CycleDetectedException(List cycle) {
            super("Cycle detected: "+Util.join(cycle," -> "));
            this.cycle = cycle;
        }
    }
}
