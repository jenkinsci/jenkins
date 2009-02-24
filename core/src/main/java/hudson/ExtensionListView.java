/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson;

import hudson.model.Hudson;
import hudson.tasks.UserNameResolver;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;

/**
 * Compatibility layer for legacy manual registration of extension points.
 *
 * <p>
 * Instances of this class can be created statically as a singleton, but it provides the view
 * to {@link ExtensionList} of the current {@link Hudson}.
 * Write operations to this list will update the legacy instances on {@link ExtensionList}.
 *
 * <p>
 * Whereas we used to use some simple data structure to keep track of static singletons,
 * we can now use this instances, so that {@link ExtensionList} sees all the auto-registered
 * and manually registered instances.
 *
 * <p>
 * Similarly, the old list (such as {@link UserNameResolver#LIST} continues to show all
 * auto and manually registered instances, thus providing necessary bi-directional interoperability.
 *
 * @author Kohsuke Kawaguchi
 */
public class ExtensionListView {
    /**
     * Creates a plain {@link List} backed by the current {@link ExtensionList}.
     */
    public static <T> List<T> createList(final Class<T> type) {
        return new AbstractList<T>() {
            private ExtensionList<T> storage() {
                return Hudson.getInstance().getExtensionList(type);
            }

            public Iterator<T> iterator() {
                return storage().iterator();
            }

            public T get(int index) {
                return storage().get(index);
            }

            public int size() {
                return storage().size();
            }
        };
    }

    // TODO: we need a few more types whose implementations get uglier
}
