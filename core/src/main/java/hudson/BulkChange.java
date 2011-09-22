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
package hudson;

import hudson.model.Saveable;

import java.io.IOException;

/**
 * Transaction-like object that can be used to make a bunch of changes to an object, and defer the
 * {@link Saveable#save()} until the end.
 *
 * <p>
 * The usage of {@link BulkChange} needs to follow a specific closure-like pattern, namely:
 *
 * <pre>
 * BulkChange bc = new BulkChange(someObject);
 * try {
 *    ... make changes to 'someObject'
 * } finally {
 *    bc.commit();
 * }
 * </pre>
 *
 * <p>
 * ... or if you'd like to avoid saving when something bad happens:
 *
 * <pre>
 * BulkChange bc = new BulkChange(someObject);
 * try {
 *    ... make changes to 'someObject'
 *    bc.commit();
 * } finally {
 *    bc.abort();
 * }
 * </pre>
 *
 * <p>
 * Use of this method is optional. If {@link BulkChange} is not used, individual mutator
 * will perform the save operation, and things will just run somewhat slower.
 *
 *
 * <h2>Cooperation from {@link Saveable}</h2>
 * <p>
 * For this class to work as intended, {@link Saveable} implementations need to co-operate.
 * Namely,
 *
 * <ol>
 * <li>
 * Mutater methods should invoke {@code this.save()} so that if the method is called outside
 * a {@link BulkChange}, the result will be saved immediately.
 *
 * <li>
 * In the {@code save()} method implementation, use {@link #contains(Saveable)} and
 * only perform the actual I/O operation when this method returns false.
 * </ol>
 *
 * <p>
 * See {@link jenkins.model.Jenkins#save()} as an example if you are not sure how to implement {@link Saveable}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.249
 */
public class BulkChange {
    private final Saveable saveable;
    public final Exception allocator;
    private final BulkChange parent;

    private boolean completed;

    public BulkChange(Saveable saveable) {
        this.parent = current();
        this.saveable = saveable;
        // rememeber who allocated this object in case
        // someone forgot to call save() at the end.
        allocator = new Exception();

        // in effect at construction
        INSCOPE.set(this);
    }

    /**
     * Saves the accumulated changes.
     */
    public void commit() throws IOException {
        if(completed)   return;
        completed = true;

        // move this object out of the scope first before save, or otherwise the save() method will do nothing.
        pop();
        saveable.save();
    }

    /**
     * Exits the scope of {@link BulkChange} without saving the changes.
     *
     * <p>
     * This can be used when a bulk change fails in the middle.
     * Note that unlike a real transaction, this will not roll back the state of the object.
     *
     * <p>
     * The abort method can be called after the commit method, in which case this method does nothing.
     * This is so that {@link BulkChange} can be used naturally in the try/finally block.
     */
    public void abort() {
        if(completed)   return;
        completed = true;
        pop();
    }

    private void pop() {
        if(current()!=this)
            throw new AssertionError("Trying to save BulkChange that's not in scope");
        INSCOPE.set(parent);
    }

    /**
     * {@link BulkChange}s that are effective currently.
     */
    private static final ThreadLocal<BulkChange> INSCOPE = new ThreadLocal<BulkChange>();

    /**
     * Gets the {@link BulkChange} instance currently in scope for the current thread.
     */
    public static BulkChange current() {
        return INSCOPE.get();
    }

    /**
     * Checks if the given {@link Saveable} is currently in the bulk change.
     *
     * <p>
     * The expected usage is from the {@link Saveable#save()} implementation to check
     * if the actual persistence should happen now or not.
     */
    public static boolean contains(Saveable s) {
        for(BulkChange b=current(); b!=null; b=b.parent)
            if(b.saveable==s || b.saveable==ALL)
                return true;
        return false;
    }

    /**
     * Magic {@link Saveable} instance that can make {@link BulkChange} veto
     * all the save operations by making the {@link #contains(Saveable)} method return
     * true for everything.
     */
    public static final Saveable ALL = new Saveable() {
        public void save() {
        }
    };
}
