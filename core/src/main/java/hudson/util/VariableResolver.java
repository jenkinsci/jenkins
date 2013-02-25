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
package hudson.util;

import java.util.Collection;
import java.util.Map;

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
    VariableResolver NONE = new VariableResolver() {
        public Object resolve(String name) {
            return null;
        }
    };

    /**
     * {@link VariableResolver} backed by a {@link Map}.
     */
    final class ByMap<V> implements VariableResolver<V> {
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
    final class Union<V> implements VariableResolver<V> {
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
