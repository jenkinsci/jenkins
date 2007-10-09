package hudson.util;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Varios {@link Iterator} implementations.
 *
 * @author Kohsuke Kawaguchi
 */
public class Iterators {
    /**
     * Returns the empty iterator.
     */
    public static <T> Iterator<T> empty() {
        return Collections.<T>emptyList().iterator();
    }

    /**
     * Produces {A,B,C,D,E,F} from {{A,B},{C},{},{D,E,F}}.
     */
    public static abstract class FlattenIterator<U,T> implements Iterator<U> {
        private final Iterator<? extends T> core;
        private Iterator<U> cur;

        protected FlattenIterator(Iterator<? extends T> core) {
            this.core = core;
            cur = Collections.<U>emptyList().iterator();
        }

        protected abstract Iterator<U> expand(T t);

        public boolean hasNext() {
            while(!cur.hasNext()) {
                if(!core.hasNext())
                    return false;
                cur = expand(core.next());
            }
            return true;
        }

        public U next() {
            if(!hasNext())  throw new NoSuchElementException();
            return cur.next();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
