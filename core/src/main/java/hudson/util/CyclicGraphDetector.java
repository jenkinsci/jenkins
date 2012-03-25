package hudson.util;

import hudson.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
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

    private final List<N> topologicalOrder = new ArrayList<N>();

    public void run(Iterable<? extends N> allNodes) throws CycleDetectedException {
        for (N n : allNodes){
            visit(n);
        }
    }

    /**
     * Returns all the nodes in the topologically sorted order.
     * That is, if there's an edge a->b, b always come earlier than a.
     */
    public List<N> getSorted() {
        return topologicalOrder;
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
        topologicalOrder.add(p);
    }

    private void detectedCycle(N q) throws CycleDetectedException {
        int i = path.indexOf(q);
        path.push(q);
        reactOnCycle(q, path.subList(i, path.size()));
    }
    
    /**
     * React on detected cycles - default implementation throws an exception.
     * @param q
     * @param cycle
     * @throws CycleDetectedException
     */
    protected void reactOnCycle(N q, List<N> cycle) throws CycleDetectedException{
        throw new CycleDetectedException(cycle);
    }    

    public static final class CycleDetectedException extends Exception {
        public final List cycle;

        public CycleDetectedException(List cycle) {
            super("Cycle detected: "+Util.join(cycle," -> "));
            this.cycle = cycle;
        }
    }
}
