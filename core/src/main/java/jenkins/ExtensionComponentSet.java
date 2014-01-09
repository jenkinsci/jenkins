/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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
package jenkins;

import com.google.common.collect.Lists;
import hudson.ExtensionComponent;
import hudson.ExtensionFinder;
import hudson.ExtensionPoint;
import hudson.model.Descriptor;
import hudson.model.Hudson;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Represents the components that's newly discovered during {@link ExtensionFinder#refresh()}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.442
 */
public abstract class ExtensionComponentSet {
    /**
     * Discover extensions of the given type.
     *
     * <p>
     * This method is called only once per the given type after all the plugins are loaded,
     * so implementations need not worry about caching.
     *
     * @param <T>
     *      The type of the extension points. This is not bound to {@link ExtensionPoint} because
     *      of {@link Descriptor}, which by itself doesn't implement {@link ExtensionPoint} for
     *      a historical reason.
     * @return
     *      Can be empty but never null.
     */
    public abstract <T> Collection<ExtensionComponent<T>> find(Class<T> type);

    /**
     * Apply {@link ExtensionFilter}s and returns a filtered set.
     */
    public final ExtensionComponentSet filtered() {
        final ExtensionComponentSet base = this;
        return new ExtensionComponentSet() {
            @Override
            public <T> Collection<ExtensionComponent<T>> find(Class<T> type) {
                List<ExtensionComponent<T>> a = Lists.newArrayList();
                for (ExtensionComponent<T> c : base.find(type)) {
                    if (ExtensionFilter.isAllowed(type,c))
                        a.add(c);
                }
                return a;
            }
        };
    }

    /**
     * Constant that has zero component in it.
     */
    public static final ExtensionComponentSet EMPTY = new ExtensionComponentSet() {
        @Override
        public <T> Collection<ExtensionComponent<T>> find(Class<T> type) {
            return Collections.emptyList();
        }
    };

    /**
     * Computes the union of all the given delta.
     */
    public static ExtensionComponentSet union(final Collection<? extends ExtensionComponentSet> base) {
        return new ExtensionComponentSet() {
            @Override
            public <T> Collection<ExtensionComponent<T>> find(Class<T> type) {
                List<ExtensionComponent<T>> r = Lists.newArrayList();
                for (ExtensionComponentSet d : base)
                    r.addAll(d.find(type));
                return r;
            }
        };
    }

    public static ExtensionComponentSet union(ExtensionComponentSet... members) {
        return union(Arrays.asList(members));
    }

    /**
     * Wraps {@link ExtensionFinder} into {@link ExtensionComponentSet}.
     */
    public static ExtensionComponentSet allOf(final ExtensionFinder f) {
        return new ExtensionComponentSet() {
            @Override
            public <T> Collection<ExtensionComponent<T>> find(Class<T> type) {
                return f.find(type, Hudson.getInstance());
            }
        };
    }
}
