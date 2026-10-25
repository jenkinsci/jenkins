/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Martin Eigenbrodt
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

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ListView;
import hudson.model.View;
import hudson.util.DescriptorList;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.export.Exported;

/**
 * Extension point for adding a column to a table rendering of {@link Item}s, such as {@link ListView}.
 *
 * <p>
 * This object must have the {@code column.jelly}. This view
 * is called for each cell of this column. The {@link Item} object
 * is passed in the "job" variable. The view should render
 * the {@code <td>} tag.
 *
 * <p>
 * This object may have an additional {@code columnHeader.jelly}. The default ColumnHeader
 * will render {@link #getColumnCaption()}.
 *
 * <p>
 * If you opt to {@linkplain ListViewColumnDescriptor#shownByDefault() be shown by default},
 * there also must be a default constructor, which is invoked to create a list view column in
 * the default configuration.
 *
 * <p>
 * Originally, this extension point was designed for {@link ListView}, but since then
 * it has grown to be applicable to other {@link View}s and {@link ItemGroup}s that render
 * a collection of {@link Item}s in a tabular format.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.279
 * @see ListViewColumnDescriptor
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
     * Returns all the registered {@link ListViewColumn} descriptors.
     */
    public static DescriptorExtensionList<ListViewColumn, Descriptor<ListViewColumn>> all() {
        return Jenkins.get().getDescriptorList(ListViewColumn.class);
    }

    /**
     * All registered {@link ListViewColumn}s.
     * @deprecated as of 1.281
     *      Use {@link #all()} for read access and {@link Extension} for registration.
     */
    @Deprecated
    public static final DescriptorList<ListViewColumn> LIST = new DescriptorList<>(ListViewColumn.class);

    /**
     * Whether this column will be shown by default.
     * The default implementation is true.
     *
     * @since 1.301
     * @deprecated as of 1.342.
     *      Use {@link ListViewColumnDescriptor#shownByDefault()}
     */
    @Deprecated
    public boolean shownByDefault() {
        return true;
    }

    /**
     * For compatibility reason, this method may not return a {@link ListViewColumnDescriptor}
     * and instead return a plain {@link Descriptor} instance.
     */
    @Override
    public Descriptor<ListViewColumn> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    /**
     * Creates the list of {@link ListViewColumn}s to be used for newly created {@link ListView}s and their likes.
     * @since 1.391
     * @deprecated use {@link #createDefaultInitialColumnList(Class)}
     */
    @Deprecated
    public static List<ListViewColumn> createDefaultInitialColumnList() {
        return createDefaultInitialColumnList(ListViewColumn.all());
    }

    /**
     * Creates the list of {@link ListViewColumn}s to be used for newly created {@link ListView}s and their likes.
     *
     * @see ListView#initColumns()
     * @since 2.37
     */
    public static List<ListViewColumn> createDefaultInitialColumnList(Class<? extends View> context) {
        return createDefaultInitialColumnList(DescriptorVisibilityFilter.applyType(context, ListViewColumn.all()));
    }

    /**
     * Creates the list of {@link ListViewColumn}s to be used for a {@link ListView}s and their likes.
     *
     * @see View#getColumns()
     * @since 2.37
     */
    public static List<ListViewColumn> createDefaultInitialColumnList(View view) {
        return createDefaultInitialColumnList(DescriptorVisibilityFilter.apply(view, ListViewColumn.all()));
    }

    private static List<ListViewColumn> createDefaultInitialColumnList(List<Descriptor<ListViewColumn>> descriptors) {
        // OK, set up default list of columns:
        // create all instances
        ArrayList<ListViewColumn> r = new ArrayList<>();
        final JSONObject emptyJSON = new JSONObject();
        for (Descriptor<ListViewColumn> d : descriptors)
            try {
                if (d instanceof ListViewColumnDescriptor ld) {
                    if (!ld.shownByDefault()) {
                        continue;   // skip this
                    }
                }
                ListViewColumn lvc = d.newInstance((StaplerRequest2) null, emptyJSON);
                if (!lvc.shownByDefault()) {
                    continue; // skip this
                }

                r.add(lvc);
            } catch (FormException e) {
                LOGGER.log(Level.WARNING, "Failed to instantiate " + d.clazz, e);
            }

        return r;
    }

    private static final Logger LOGGER = Logger.getLogger(ListViewColumn.class.getName());

    /*
        Standard ordinal positions for built-in ListViewColumns.

        There are icons at the very left that are generally used to show status,
        then item name that comes in at the very end of that icon set.

        Then the section of "properties" that show various properties of the item in text.

        Finally, the section of action icons at the end.
     */
    public static final double DEFAULT_COLUMNS_ORDINAL_ICON_START = 60;
    public static final double DEFAULT_COLUMNS_ORDINAL_ICON_END = 50;
    public static final double DEFAULT_COLUMNS_ORDINAL_PROPERTIES_START = 40;
    public static final double DEFAULT_COLUMNS_ORDINAL_PROPERTIES_END = 30;
    public static final double DEFAULT_COLUMNS_ORDINAL_ACTIONS_START = 20;
    public static final double DEFAULT_COLUMNS_ORDINAL_ACTIONS_END = 10;
}
