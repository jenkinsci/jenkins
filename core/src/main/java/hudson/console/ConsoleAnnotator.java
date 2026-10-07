/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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

package hudson.console;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.MarkupText;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;

/**
 * Annotates one line of console output.
 *
 * <p>
 * In Jenkins, console output annotation is done line by line, and
 * we model this as a state machine &mdash;
 * the code encapsulates some state, and it uses that to annotate one line (and possibly update the state.)
 *
 * <p>
 * A {@link ConsoleAnnotator} instance encapsulates this state, and the {@link #annotate(Object, MarkupText)}
 * method is used to annotate the next line based on the current state. The method returns another
 * {@link ConsoleAnnotator} instance that represents the altered state for annotating the next line.
 *
 * <p>
 * {@link ConsoleAnnotator}s are run when a browser requests console output, and the above-mentioned chain
 * invocation is done for each client request separately. Therefore, logically you can think of this process as:
 *
 * <pre>
 * ConsoleAnnotator ca = ...;
 * ca.annotate(context,line1).annotate(context,line2)...
 * </pre>
 *
 * <p>
 * Because a browser can request console output incrementally, in addition to above a console annotator
 * can be serialized at any point and deserialized back later to continue annotation where it left off.
 *
 * <p>
 * {@link ConsoleAnnotator} instances can be created in a few different ways. See {@link ConsoleNote}
 * and {@link ConsoleAnnotatorFactory}.
 *
 * @author Kohsuke Kawaguchi
 * @see ConsoleAnnotatorFactory
 * @see ConsoleNote
 * @since 1.349
 */
public abstract class ConsoleAnnotator<T> implements Serializable {
    /**
     * Annotates one line.
     *
     * @param context
     *      The object that owns the console output. Never {@code null}.
     * @param text
     *      Contains a single line of console output, and defines convenient methods to add markup.
     *      The callee should put markup into this object. Never {@code null}.
     * @return
     *      The {@link ConsoleAnnotator} object that will annotate the next line of the console output.
     *      To indicate that you are not interested in the following lines, return {@code null}.
     */
    @CheckForNull
    public abstract ConsoleAnnotator<T> annotate(@NonNull T context, @NonNull MarkupText text);

    /**
     * Cast operation that restricts T.
     */
    @SuppressWarnings("unchecked")
    public static <T> ConsoleAnnotator<T> cast(ConsoleAnnotator<? super T> a) {
        return (ConsoleAnnotator) a;
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // unclear to jglick what is going on here
    private static final class ConsoleAnnotatorAggregator<T> extends ConsoleAnnotator<T> {
        List<ConsoleAnnotator<T>> list;

        ConsoleAnnotatorAggregator(Collection list) {
            this.list = new ArrayList<>(list);
        }

        @Override
        public ConsoleAnnotator annotate(T context, MarkupText text) {
            ListIterator<ConsoleAnnotator<T>> itr = list.listIterator();
            while (itr.hasNext()) {
                ConsoleAnnotator a =  itr.next();
                ConsoleAnnotator b = a.annotate(context, text);
                if (a != b) {
                    if (b == null)    itr.remove();
                    else            itr.set(b);
                }
            }

            return switch (list.size()) {
                case 0 -> null;             // no more annotator left
                case 1 -> list.getFirst();  // no point in aggregating
                default -> this;
            };
        }

        @Override
        public String toString() {
            return "ConsoleAnnotatorAggregator" + list;
        }
    }

    /**
     * Bundles all the given {@link ConsoleAnnotator} into a single annotator.
     */
    public static <T> ConsoleAnnotator<T> combine(Collection<? extends ConsoleAnnotator<? super T>> all) {
        return switch (all.size()) {
            case 0 -> null;                         // none
            case 1 -> cast(all.iterator().next());  // just one
            default -> new ConsoleAnnotatorAggregator<>(all);
        };
    }

    /**
     * Returns the all {@link ConsoleAnnotator}s for the given context type aggregated into a single
     * annotator.
     */
    public static <T> ConsoleAnnotator<T> initial(T context) {
        return combine(_for(context));
    }

    /**
     * List all the console annotators that can work for the specified context type.
     */
    @SuppressWarnings({"unchecked", "rawtypes"}) // reflective
    public static <T> List<ConsoleAnnotator<T>> _for(T context) {
        List<ConsoleAnnotator<T>> r  = new ArrayList<>();
        for (ConsoleAnnotatorFactory f : ConsoleAnnotatorFactory.all()) {
            if (f.type().isInstance(context)) {
                ConsoleAnnotator ca = f.newInstance(context);
                if (ca != null)
                    r.add(ca);
            }
        }
        return r;
    }

    private static final long serialVersionUID = 1L;
}
