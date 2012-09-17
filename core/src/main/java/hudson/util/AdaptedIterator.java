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

import java.util.Iterator;

/**
 * {@link Iterator} that adapts the values returned from another iterator.
 *
 * <p>
 * This class should be really in {@link Iterators} but for historical reasons it's here.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.121
 * @see Iterators
 */
public abstract class AdaptedIterator<T,U> implements Iterator<U> {
    private final Iterator<? extends T> core;

    protected AdaptedIterator(Iterator<? extends T> core) {
        this.core = core;
    }

    protected AdaptedIterator(Iterable<? extends T> core) {
        this(core.iterator());
    }

    public boolean hasNext() {
        return core.hasNext();
    }

    public U next() {
        return adapt(core.next());
    }

    protected abstract U adapt(T item);

    public void remove() {
        core.remove();
    }
}
