/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Michael B. Donohue
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

import hudson.tasks.test.TestResultProjectAction;

/**
 * Object that contributes an item to the left hand side menu
 * of a {@link ModelObject}
 * (for example to {@link Project}, {@link Build}, and etc.)
 *
 * <p>
 * If an action has a view named <tt>floatingBox.jelly</tt>,
 * it will be displayed as a floating box on the top page of
 * the target {@link ModelObject}. (For example, this is how
 * the JUnit test result trend shows up in the project top page.
 * See {@link TestResultProjectAction}.
 *
 * <h2>Persistence</h2>
 * <p>
 * Actions are often persisted as a part of {@link Actionable}
 * (for example with {@link Build}) via XStream. In some other cases,
 * {@link Action}s are transient and not persisted (such as
 * when it's used with {@link Job}).
 *
 * @author Kohsuke Kawaguchi
 */
public interface Action extends ModelObject {
    /**
     * Gets the file name of the icon.
     *
     * @return
     *      If just a file name (like "abc.gif") is returned, it will be
     *      interpreted as a file name inside <tt>/images/24x24</tt>.
     *      This is useful for using one of the stock images.
     *      <p>
     *      If an absolute file name that starts from '/' is returned (like
     *      "/plugin/foo/abc.gif'), then it will be interpreted as a path
     *      from the context root of Hudson. This is useful to pick up
     *      image files from a plugin.
     *      <p>
     *      Finally, return null to hide it from the task list. This is normally not very useful,
     *      but this can be used for actions that only contribute <tt>floatBox.jelly</tt>
     *      and no task list item. The other case where this is useful is
     *      to avoid showing links that require a privilege when the user is anonymous.
     * @see Hudson#isAdmin()
     */
    String getIconFileName();

    /**
     * Gets the string to be displayed.
     *
     * The convention is to capitalize the first letter of each word,
     * such as "Test Result". 
     */
    String getDisplayName();

    /**
     * Gets the URL path name.
     *
     * <p>
     * For example, if this method returns "xyz", and if the parent object
     * (that this action is associated with) is bound to /foo/bar/zot,
     * then this action object will be exposed to /foo/bar/zot/xyz.
     *
     * <p>
     * This method should return a string that's unique among other {@link Action}s.
     *
     * <p>
     * The returned string can be an absolute URL, like "http://www.sun.com/",
     * which is useful for directly connecting to external systems.
     *
     * <p>
     * If the returned string starts with '/', like '/foo', then it's assumed to be
     * relative to the context path of the Hudson webapp. 
     *
     * @return
     *      null if this action object doesn't need to be bound to web
     *      (when you do that, be sure to also return null from {@link #getIconFileName()}.
     */
    String getUrlName();
}
