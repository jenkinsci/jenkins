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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.ExtensionPoint;
import jenkins.management.Badge;

/**
 * Marker interface for actions that are added to {@link jenkins.model.Jenkins}.
 *
 * <p>
 * Extend from this interface and put {@link Extension} on your subtype
 * to have them auto-registered to {@link jenkins.model.Jenkins}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.311
 */
public interface RootAction extends Action, ExtensionPoint {

    /**
     * A {@link Badge} shown on the button for the action.
     *
     * @return badge or {@code null} if no badge should be shown.
     * @since 2.507
     */
    default @CheckForNull Badge getBadge() {
        return null;
    }
}
