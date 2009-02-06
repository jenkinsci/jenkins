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
package hudson.views;

import hudson.ExtensionPoint;
import hudson.tasks.Publisher;
import hudson.model.Describable;
import hudson.model.ListView;
import hudson.model.Item;
import hudson.util.DescriptorList;
import org.kohsuke.stapler.export.Exported;

/**
 * Extension point for adding a column to {@link ListView}.
 *
 * <p>
 * This object must have the <tt>cell.jelly</tt>. This view
 * is called for each cell of this column. The {@link Item} object
 * is passed in the "job" variable. The view should render
 * the &lt;td> tag.
 *
 * <p>
 * For now, {@link ListView} doesn't allow {@link ListViewColumn}s to be configured
 * (instead it just shows all the columns available in {@link #LIST}),
 * but the intention is eventually make each {@link ListViewColumn} fully configurable
 * like {@link Publisher}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.279
 */
public abstract class ListViewColumn implements ExtensionPoint, Describable<ListViewColumn> {
    /**
     * Returns the name of the column that explains what this column means
     *
     * @return
     *      The convention is to use capitalization like "Foo Bar Zot".
     */
    @Exported
    public String getColumnCaption() {
        return getDescriptor().getDisplayName();
    }

    /**
     * All registered {@link ListViewColumn}s.
     */
    public static final DescriptorList<ListViewColumn> LIST = new DescriptorList<ListViewColumn>();
}
