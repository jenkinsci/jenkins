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

/**
 * Activity that requires certain resources for its execution.
 *
 * @author Kohsuke Kawaguchi
 */
public interface ResourceActivity extends ModelObject {
    /**
     * Gets the list of {@link Resource}s that this task requires.
     * Used to make sure no two conflicting tasks run concurrently.
     *
     * <p>
     * This method must always return the {@link ResourceList}
     * that contains the exact same set of {@link Resource}s.
     *
     * <p>
     * If the activity doesn't lock any resources, just
     * return {@link ResourceList#EMPTY} (or decline to override).
     *
     * @return never null
     */
    default ResourceList getResourceList() {
        return ResourceList.EMPTY;
    }
}
