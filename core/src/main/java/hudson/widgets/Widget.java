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
package hudson.widgets;

import hudson.ExtensionPoint;
import hudson.model.View;

/**
 * Box to be rendered in the side panel.
 *
 * <h2>Views</h2>
 * <ul>
 * <li><b>index.jelly</b> should display the widget. It should have:
 *   &lt;l:pane width="2" title="..."> ...body... &lt;/l:pane> structure.
 *   In this view, "it" points to the {@link Widget} and "view" points to {@link View}
 *   that's rendering the widget.
 * </ul>
 *
 * TODO:
 *  - make Widget describable & provide the UI to let admin configure widgets?
 *    - backward compatibility implications?
 *
 *
 * @author Kohsuke Kawaguchi
 * @since 1.146
 * @see jenkins.model.Jenkins#getWidgets()
 */
public abstract class Widget implements ExtensionPoint {
    /**
     * Gets the URL path name.
     *
     * <p>
     * For example, if this method returns "xyz", and if the parent object
     * (that this widget is associated with) is bound to /foo/bar/zot,
     * then this widget object will be exposed to /foo/bar/zot/xyz.
     *
     * <p>
     * This method is useful when the widget needs to expose additional URLs,
     * for example for serving AJAX requests.
     *
     * <p>
     * This method should return a string that's unique among other {@link Widget}s.
     * The default implementation returns the unqualified class name.
     */
    public String getUrlName() {
        return getClass().getSimpleName();
    }
}
