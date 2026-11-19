/*
 * The MIT License
 *
 * Copyright (c) 2026, the contributors
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

package jenkins.util;

import java.util.Iterator;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * An iterable that can check for interruption even while skipping elements inside {@link Iterator#hasNext()}.
 * The check belongs to an individual scan; ordinary iteration does not check for interruption.
 */
@Restricted(NoExternalUse.class)
public interface InterruptibleIterable<T> extends Iterable<T> {

    /**
     * Creates an iterator that invokes the supplied check while scanning and passes it to nested iterables.
     */
    Iterator<T> iterator(Runnable checkInterrupted);

    @Override
    default Iterator<T> iterator() {
        return iterator(() -> {});
    }

    /**
     * Passes a scan's interruption check to iterables that support it.
     * Other iterables retain their ordinary iterator behavior.
     */
    static <T> Iterator<T> iterator(Iterable<T> source, Runnable checkInterrupted) {
        return source instanceof InterruptibleIterable<T> interruptible
                ? interruptible.iterator(checkInterrupted) : source.iterator();
    }
}
