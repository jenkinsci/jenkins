package hudson.util;

import java.util.Iterator;

/**
 * {@link Iterator} that adapts the values returned from another iterator.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.121
 */
public abstract class AdaptedIterator<T,U> implements Iterator<U> {
    private final Iterator<? extends T> core;

    protected AdaptedIterator(Iterator<? extends T> core) {
        this.core = core;
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
