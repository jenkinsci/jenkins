package hudson.util;

import java.util.Map;
import java.util.Collection;

/**
 * Resolves variables to its value, while encapsulating
 * how that resolution happens.
 *
 * @author Kohsuke Kawaguchi
 */
public interface VariableResolver<V> {
    /**
     * Receives a variable name and obtains the value associated with the name.
     *
     * <p>
     * This can be implemented simply on top of a {@link Map} (see {@link ByMap}), or
     * this can be used like an expression evaluator.
     *
     * @param name
     *      Name of the variable to be resolved.
     *      Never null, never empty. The name shouldn't include the syntactic
     *      marker of an expression. IOW, it should be "foo" but not "${foo}".
     *      A part of the goal of this design is to abstract away the expression
     *      marker syntax. 
     * @return
     *      Object referenced by the name.
     *      Null if not found.
     */
    V resolve(String name);

    /**
     * Empty resolver that always returns null.
     */
    public static final VariableResolver NONE = new VariableResolver() {
        public Object resolve(String name) {
            return null;
        }
    };

    /**
     * {@link VariableResolver} backed by a {@link Map}.
     */
    public static final class ByMap<V> implements VariableResolver<V> {
        private final Map<String,V> data;

        public ByMap(Map<String, V> data) {
            this.data = data;
        }

        public V resolve(String name) {
            return data.get(name);
        }
    }

    /**
     * Union of multiple {@link VariableResolver}.
     */
    public static final class Union<V> implements VariableResolver<V> {
        private final VariableResolver<? extends V>[] resolvers;

        public Union(VariableResolver<? extends V>... resolvers) {
            this.resolvers = resolvers.clone();
        }

        public Union(Collection<? extends VariableResolver<? extends V>> resolvers) {
            this.resolvers = resolvers.toArray(new VariableResolver[resolvers.size()]);
        }

        public V resolve(String name) {
            for (VariableResolver<? extends V> r : resolvers) {
                V v = r.resolve(name);
                if(v!=null) return v;
            }
            return null;
        }
    }
}
