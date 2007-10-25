package hudson.util;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.ListIterator;
import java.util.AbstractList;

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

        protected FlattenIterator(Iterable<? extends T> core) {
            this(core.iterator());
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

    /**
     * Returns the {@link Iterable} that lists items in the reverse order.
     */
    public static <T> Iterable<T> reverse(final List<T> lst) {
        return new Iterable<T>() {
            public Iterator<T> iterator() {
                final ListIterator<T> itr = lst.listIterator(lst.size());
                return new Iterator<T>() {
                    public boolean hasNext() {
                        return itr.hasPrevious();
                    }

                    public T next() {
                        return itr.previous();
                    }

                    public void remove() {
                        itr.remove();
                    }
                };
            }
        };
    }

    /**
     * Returns a list that represents [start,end).
     *
     * For example sequence(1,5,1)={1,2,3,4}, and sequence(7,1,-2)={7.5,3}
     */
    public static List<Integer> sequence(final int start, int end, final int step) {

        final int size = (end-start)/step;
        if(size<0)  throw new IllegalArgumentException("List size is negative");

        return new AbstractList<Integer>() {
            public Integer get(int index) {
                return start+index*step;
            }

            public int size() {
                return size;
            }
        };
    }

    public static List<Integer> sequence(int start, int end) {
        return sequence(start,end,1);
    }

    /**
     * The short cut for {@code reverse(sequence(start,end,step))}.
     */
    public static List<Integer> reverseSequence(int start, int end, int step) {
        return sequence(end-1,start-1,-step);
    }

    public static List<Integer> reverseSequence(int start, int end) {
        return reverseSequence(start,end,1);
    }
}
