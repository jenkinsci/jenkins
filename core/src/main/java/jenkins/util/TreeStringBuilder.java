package jenkins.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds {@link TreeString}s that share common prefixes. Call
 * {@link #intern(String)} and you get the {@link TreeString} that represents
 * the same string, but as you interns more strings that share the same
 * prefixes, those {@link TreeString}s that you get back start to share data.
 * <p>
 * Because the internal state of {@link TreeString}s get mutated as new strings
 * are interned (to exploit new-found common prefixes), {@link TreeString}s
 * returned from {@link #intern(String)} aren't thread-safe until
 * {@link TreeStringBuilder} is disposed. That is, you have to make sure other
 * threads don't see those {@link TreeString}s until you are done interning
 * strings.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.473
 */
public class TreeStringBuilder {
    Child root = new Child(new TreeString());

    private static class Child {
        private final TreeString node;

        private Map<String, Child> children = NO_CHILDREN;

        private Child(final TreeString node) {
            this.node = node;
        }

        /**
         * Adds one edge and leaf to this tree node, or returns an existing node
         * if any.
         */
        public Child intern(final String s) {
            if (s.isEmpty()) {
                return this;
            }

            makeWritable();
            for (Map.Entry<String, Child> e : children.entrySet()) {
                int plen = commonPrefix(e.getKey(), s);
                if (plen > 0) {
                    if (plen < e.getKey().length()) {
                        // insert a node between this and e.value
                        Child c = e.getValue();
                        String prefix = s.substring(0, plen);
                        Child middle = c.split(prefix);

                        // add 'middle' instead of 'c'
                        children.remove(e.getKey());
                        children.put(prefix, middle);

                        return middle.intern(s.substring(plen));
                    }
                    else { // entire key is suffix
                        return e.getValue().intern(s.substring(plen));
                    }
                }
            }

            // no common prefix. an entirely new node.
            Child t = children.get(s);
            if (t == null) {
                children.put(s, t = new Child(new TreeString(node, s)));
            }
            return t;
        }

        /**
         * Makes sure {@link #children} is writable.
         */
        private void makeWritable() {
            if (children == NO_CHILDREN) {
                children = new HashMap<>();
            }
        }

        /**
         * Inserts a new node between this node and its parent, and returns that
         * node. Newly inserted 'middle' node will have this node as its sole
         * child.
         */
        private Child split(final String prefix) {
            String suffix = node.getLabel().substring(prefix.length());

            Child middle = new Child(node.split(prefix));
            middle.makeWritable();
            middle.children.put(suffix, this);

            return middle;
        }

        /**
         * Returns the common prefix between two strings.
         */
        private int commonPrefix(final String a, final String b) {
            int m = Math.min(a.length(), b.length());

            for (int i = 0; i < m; i++) {
                if (a.charAt(i) != b.charAt(i)) {
                    return i;
                }
            }
            return m;
        }

        /**
         * Calls {@link TreeString#dedup(Map)} recursively.
         */
        private void dedup(final Map<String, char[]> table) {
            node.dedup(table);
            for (Child child : children.values()) {
                child.dedup(table);
            }
        }
    }

    /**
     * Interns a string.
     */
    public TreeString intern(final String s) {
        if (s == null)    return null;
        return root.intern(s).node;
    }

    /**
     * Interns a {@link TreeString} created elsewhere.
     */
    public TreeString intern(final TreeString s) {
        if (s == null)    return null;
        return root.intern(s.toString()).node;
    }

    /**
     * Further reduces the memory footprint by finding the same labels across
     * multiple {@link TreeString}s.
     */
    public void dedup() {
        root.dedup(new HashMap<>());
    }

    /**
     * Place holder that represents no child node, until one is added.
     */
    private static final Map<String, Child> NO_CHILDREN = Collections.emptyMap();

}
