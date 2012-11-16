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

import com.google.common.base.Predicates;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.ListIterator;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;

/**
 * Varios {@link Iterator} implementations.
 *
 * @author Kohsuke Kawaguchi
 * @see AdaptedIterator
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
     * Creates a filtered view of another iterator.
     *
     * @since 1.150
     */
    public static abstract class FilterIterator<T> implements Iterator<T> {
        private final Iterator<? extends T> core;
        private T next;
        private boolean fetched;

        protected FilterIterator(Iterator<? extends T> core) {
            this.core = core;
        }

        protected FilterIterator(Iterable<? extends T> core) {
            this(core.iterator());
        }

        private void fetch() {
            while(!fetched && core.hasNext()) {
                T n = core.next();
                if(filter(n)) {
                    next = n;
                    fetched = true;
                }
            }
        }

        /**
         * Filter out items in the original collection.
         *
         * @return
         *      true to leave this item and return this item from this iterator.
         *      false to hide this item.
         */
        protected abstract boolean filter(T t);

        public boolean hasNext() {
            fetch();
            return fetched;
        }

        public T next() {
            fetch();
            if(!fetched)  throw new NoSuchElementException();
            fetched = false;
            return next;
        }

        public void remove() {
            core.remove();
        }
    }

    /**
     * Remove duplicates from another iterator.
     */
    public static final class DuplicateFilterIterator<T> extends FilterIterator<T> {
        private final Set<T> seen = new HashSet<T>();

        public DuplicateFilterIterator(Iterator<? extends T> core) {
            super(core);
        }

        public DuplicateFilterIterator(Iterable<? extends T> core) {
            super(core);
        }

        protected boolean filter(T t) {
            return seen.add(t);
        }
    }

    /**
     * Returns the {@link Iterable} that lists items in the reverse order.
     *
     * @since 1.150
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
     * Returns an {@link Iterable} that lists items in the normal order
     * but which hides the base iterator implementation details.
     *
     * @since 1.492
     */
    public static <T> Iterable<T> wrap(final Iterable<T> base) {
        return new Iterable<T>() {
            public Iterator<T> iterator() {
                final Iterator<T> itr = base.iterator();
                return new Iterator<T>() {
                    public boolean hasNext() {
                        return itr.hasNext();
                    }

                    public T next() {
                        return itr.next();
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
     *
     * @since 1.150
     */
    public static List<Integer> sequence(final int start, int end, final int step) {

        final int size = (end-start)/step;
        if(size<0)  throw new IllegalArgumentException("List size is negative");

        return new AbstractList<Integer>() {
            public Integer get(int index) {
                if(index<0 || index>=size)
                    throw new IndexOutOfBoundsException();
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
     *
     * @since 1.150
     */
    public static List<Integer> reverseSequence(int start, int end, int step) {
        return sequence(end-1,start-1,-step);
    }

    public static List<Integer> reverseSequence(int start, int end) {
        return reverseSequence(start,end,1);
    }

    /**
     * Casts {@link Iterator} by taking advantage of its covariant-ness.
     */
    @SuppressWarnings({"unchecked"})
    public static <T> Iterator<T> cast(Iterator<? extends T> itr) {
        return (Iterator)itr;
    }

    /**
     * Casts {@link Iterable} by taking advantage of its covariant-ness.
     */
    @SuppressWarnings({"unchecked"})
    public static <T> Iterable<T> cast(Iterable<? extends T> itr) {
        return (Iterable)itr;
    }

    /**
     * Returns an {@link Iterator} that only returns items of the given subtype.
     */
    @SuppressWarnings({"unchecked"})
    public static <U,T extends U> Iterator<T> subType(Iterator<U> itr, final Class<T> type) {
        return (Iterator)new FilterIterator<U>(itr) {
            protected boolean filter(U u) {
                return type.isInstance(u);
            }
        };
    }

    /**
     * Creates a read-only mutator that disallows {@link Iterator#remove()}.
     */
    public static <T> Iterator<T> readOnly(final Iterator<T> itr) {
        return new Iterator<T>() {
            public boolean hasNext() {
                return itr.hasNext();
            }

            public T next() {
                return itr.next();
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Wraps another iterator and throws away nulls.
     */
    public static <T> Iterator<T> removeNull(final Iterator<T> itr) {
        return com.google.common.collect.Iterators.filter(itr, Predicates.notNull());
    }

    /**
     * Returns an {@link Iterable} that iterates over all the given {@link Iterable}s.
     *
     * <p>
     * That is, this creates {A,B,C,D} from {A,B},{C,D}.
     */
    public static <T> Iterable<T> sequence( final Iterable<? extends T>... iterables ) {
        return new Iterable<T>() {
            public Iterator<T> iterator() {
                return new FlattenIterator<T,Iterable<? extends T>>(Arrays.asList(iterables)) {
                    protected Iterator<T> expand(Iterable<? extends T> iterable) {
                        return cast(iterable).iterator();
                    }
                };
            }
        };
    }

    /**
     * Filters another iterator by eliminating duplicates.
     */
    public static <T> Iterator<T> removeDups(Iterator<T> iterator) {
        return new FilterIterator<T>(iterator) {
            final Set<T> found = new HashSet<T>();
            @Override
            protected boolean filter(T t) {
                return found.add(t);
            }
        };
    }

    /**
     * Filters another iterator by eliminating duplicates.
     */
    public static <T> Iterable<T> removeDups(final Iterable<T> base) {
        return new Iterable<T>() {
            public Iterator<T> iterator() {
                return removeDups(base.iterator());
            }
        };
    }

    public static <T> Iterator<T> sequence(Iterator<? extends T>... iterators) {
        return com.google.common.collect.Iterators.concat(iterators);
    }

    /**
     * Returns the elements in the base iterator until it hits any element that doesn't satisfy the filter.
     * Then the rest of the elements in the base iterator gets ignored.
     *
     * @since 1.485
     */
    public static <T> Iterator<T> limit(final Iterator<? extends T> base, final CountingPredicate<? super T> filter) {
        return new Iterator<T>() {
            private T next;
            private boolean end;
            private int index=0;
            public boolean hasNext() {
                fetch();
                return next!=null;
            }

            public T next() {
                fetch();
                T r = next;
                next = null;
                return r;
            }

            private void fetch() {
                if (next==null && !end) {
                    if (base.hasNext()) {
                        next = base.next();
                        if (!filter.apply(index++,next)) {
                            next = null;
                            end = true;
                        }
                    } else {
                        end = true;
                    }
                }
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public interface CountingPredicate<T> {
        boolean apply(int index, T input);
    }
}
