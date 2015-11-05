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
package hudson.model;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents things that {@link hudson.model.Queue.Executable} uses while running.
 *
 * <p>
 * This is used in {@link Queue} to support basic mutual exclusion/locks. If two
 * {@link hudson.model.Queue.Task}s require the same {@link Resource}, they will not
 * be run at the same time.
 *
 * <p>
 * Resources are compared by using their {@link #displayName names}, and
 * need not have the "same object" semantics.
 *
 * @since 1.121
 */
public final class Resource {
    /**
     * Human-readable name of this resource.
     * Used for rendering HTML.
     */
    public final @Nonnull String displayName;

    /**
     * Parent resource.
     *
     * <p>
     * A child resource is considered a part of the parent resource,
     * so acquiring the parent resource always imply acquiring all
     * the child resources.
     */
    public final @CheckForNull Resource parent;

    /**
     * Maximum number of concurrent write.
     */
    public final int numConcurrentWrite;

    public Resource(@CheckForNull Resource parent, @Nonnull String displayName) {
        this(parent,displayName,1);
    }

    /**
     * @since 1.155
     */
    public Resource(@CheckForNull Resource parent, @Nonnull String displayName, int numConcurrentWrite) {
        if(numConcurrentWrite<1)
            throw new IllegalArgumentException();

        this.parent = parent;
        this.displayName = displayName;
        this.numConcurrentWrite = numConcurrentWrite;
    }

    public Resource(@Nonnull String displayName) {
        this(null,displayName);
    }

    /**
     * Checks the resource collision.
     *
     * @param count
     *      If we are testing W/W conflict, total # of write counts.
     *      For R/W conflict test, this value should be set to {@link Integer#MAX_VALUE}.
     */
    public boolean isCollidingWith(Resource that, int count) {
        assert that!=null;
        for(Resource r=that; r!=null; r=r.parent)
            if(this.equals(r) && r.numConcurrentWrite<count)
                return true;
        for(Resource r=this; r!=null; r=r.parent)
            if(that.equals(r) && r.numConcurrentWrite<count)
                return true;
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Resource that = (Resource) o;

        return displayName.equals(that.displayName) && eq(this.parent,that.parent);
    }

    private static boolean eq(Object lhs,Object rhs) {
        if(lhs==rhs)    return true;
        if(lhs==null || rhs==null)  return false;
        return lhs.equals(rhs);
    }

    @Override
    public int hashCode() {
        return displayName.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        if(parent!=null)
            buf.append(parent).append('/');
        buf.append(displayName).append('(').append(numConcurrentWrite).append(')');
        return buf.toString();
    }
}
