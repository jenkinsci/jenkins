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
