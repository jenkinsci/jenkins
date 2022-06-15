package hudson.util;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import hudson.util.CyclicGraphDetector.CycleDetectedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class CyclicGraphDetectorTest {

    private static class Edge {
        String src, dst;

        private Edge(String src, String dst) {
            this.src = src;
            this.dst = dst;
        }
    }

    private static class Graph extends ArrayList<Edge> {
        Graph e(String src, String dst) {
            add(new Edge(src, dst));
            return this;
        }

        Set<String> nodes() {
            Set<String> nodes = new LinkedHashSet<>();
            for (Edge e : this) {
                nodes.add(e.src);
                nodes.add(e.dst);
            }
            return nodes;
        }

        Set<String> edges(String from) {
            Set<String> edges = new LinkedHashSet<>();
            for (Edge e : this) {
                if (e.src.equals(from))
                    edges.add(e.dst);
            }
            return edges;
        }

        /**
         * Performs a cycle check.
         */
        void check() throws Exception {
            new CyclicGraphDetector<String>() {
                @Override
                protected Set<String> getEdges(String s) {
                    return edges(s);
                }
            }.run(nodes());
        }

        void mustContainCycle(String... members) {
            final CycleDetectedException e = assertThrows("Cycle expected",
                    CycleDetectedException.class, this::check);

            final String msg = "Expected cycle of " + Arrays.asList(members) + " but found " + e.cycle;
            for (String s : members) {
                assertTrue(msg, e.cycle.contains(s));
            }
        }
    }

    @Test
    public void cycle1() {
        new Graph().e("A", "B").e("B", "C").e("C", "A").mustContainCycle("A", "B", "C");
    }

    @Test
    public void cycle2() {
        new Graph().e("A", "B").e("B", "C").e("C", "C").mustContainCycle("C");
    }

    @Test
    public void cycle3() {
        new Graph().e("A", "B").e("B", "C").e("C", "D").e("B", "E").e("E", "D").e("E", "A").mustContainCycle("A", "B", "E");
    }
}
