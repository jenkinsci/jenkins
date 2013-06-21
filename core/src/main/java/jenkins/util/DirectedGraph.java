package jenkins.util;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * A possible cyclic directed graph.
 *
 * This class defines various algorithms on a directed graph that's not necessarily acyclic.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class DirectedGraph<N> {
    /**
     * All the vertices of the nodes.
     */
    protected abstract Collection<N> nodes();

    /**
     * Forward traversal of the edges.
     */
    protected abstract Collection<N> forward(N node);

    /**
     * Strongly connected component (SCC) of a graph.
     */
    public static class SCC<N> extends AbstractSet<N> {
        /**
         * The Tarjan's algorithm is such that this index constitutes
         * the reverse topological order of the topological sort of the SCC DAG.
         *
         * <p>
         * That is, if you think about a derived graph where nodes are SCCs of the original directed graph,
         * it will always form a DAG even when the original graph has cycles.
         *
         * Smallest SCC# means it's more of a sink, and larger SCC# means it's more of a source.
         */
        public final int index;

        private final List<N> members = new ArrayList<N>();

        public SCC(int index) {
            this.index = index;
        }

        @Override
        public Iterator<N> iterator() {
            return members.iterator();
        }

        @Override
        public int size() {
            return members.size();
        }
    }

    /**
     * Node of the cyclic graph, which is primarily {@link N} but with additional
     * data structures needed for the Tarjan's algorithm.
     */
    class Node {
        final N n;
        /**
         * DFS visit order.
         */
        int index = -1;
        /**
         * The smallest index of any nodes reachable from this node transitively.
         */
        int lowlink;

        SCC scc;

        Node(N n) {
            this.n = n;
        }

        Collection<N> edges() {
            return forward(n);
        }
    }

    /**
     * Performs the Tarjan's algorithm and computes strongly-connected components from the
     * sink to source order.
     *
     * See http://en.wikipedia.org/wiki/Tarjan's_strongly_connected_components_algorithm
     */
    public List<SCC<N>> getStronglyConnectedComponents() {
        final Map<N, Node> nodes = new HashMap<N, Node>();
        for (N n : nodes()) {
            nodes.put(n,new Node(n));
        }

        final List<SCC<N>> sccs = new ArrayList<SCC<N>>();

        class Tarjan {
            int index = 0;
            int sccIndex = 0;
            /**
             * Nodes not yet classified for the strongly connected components
             */
            Stack<Node> pending = new Stack<Node>();
            
            void traverse() {
                for (Node n : nodes.values()) {
                    if (n.index==-1)
                        visit(n);
                }
            }
            
            void visit(Node v) {
                v.index = v.lowlink = index++;
                pending.push(v);

                for (N q : v.edges()) {
                    Node w = nodes.get(q);
                    if (w.index==-1) {
                        visit(w);
                        v.lowlink = Math.min(v.lowlink,w.lowlink);
                    } else
                    if (pending.contains(w)) {
                        v.lowlink = Math.min(v.lowlink,w.index);
                    }
                }

                if (v.lowlink==v.index) {
                    // found a new SCC
                    SCC<N> scc = new SCC<N>(sccIndex++);
                    sccs.add(scc);

                    Node w;
                    do {
                        w = pending.pop();
                        w.scc = scc;
                        scc.members.add(w.n);
                    } while(w!=v);
                }
            }
        }

        new Tarjan().traverse();

        Collections.reverse(sccs);

        return sccs;
    }
}
