/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt, Seiji Sogabe, Martin Eigenbrodt
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

import hudson.DescriptorExtensionList;
import hudson.Util;
import hudson.Extension;
import hudson.views.BuildButtonColumn;
import hudson.views.JobColumn;
import hudson.views.LastDurationColumn;
import hudson.views.LastFailureColumn;
import hudson.views.LastSuccessColumn;
import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;
import hudson.views.StatusColumn;
import hudson.views.WeatherColumn;
import hudson.model.Descriptor.FormException;
import hudson.util.CaseInsensitiveComparator;
import hudson.util.DescribableList;
import hudson.util.FormValidation;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.SortedSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Displays {@link Job}s in a flat list view.
 *
 * @author Kohsuke Kawaguchi
 */
public class ListView extends View implements Saveable {

    /**
     * List of job names. This is what gets serialized.
     */
    /*package*/ final SortedSet<String> jobNames = new TreeSet<String>(CaseInsensitiveComparator.INSTANCE);

    private DescribableList<ListViewColumn, Descriptor<ListViewColumn>> columns;

    /**
     * Include regex string.
     */
    private String includeRegex;
    
    /**
     * Compiled include pattern from the includeRegex string.
     */
    private transient Pattern includePattern;

    @DataBoundConstructor
    public ListView(String name) {
        super(name);
        initColumns();
    }

    public ListView(String name, ViewGroup owner) {
        this(name);
        this.owner = owner;
    }

    public void save() throws IOException {
        // persistence is a part of the owner.
        // due to the initialization timing issue, it can be null when this method is called.
        if (owner!=null)
            owner.save();
    }

    private Object readResolve() {
        if(includeRegex!=null)
            includePattern = Pattern.compile(includeRegex);
        initColumns();
        return this;
    }

    protected void initColumns() {
        if (columns != null) {
            // already persisted
            return;
        }

        // OK, set up default list of columns:
        // create all instances
        ArrayList<ListViewColumn> r = new ArrayList<ListViewColumn>();
        DescriptorExtensionList<ListViewColumn, Descriptor<ListViewColumn>> all = ListViewColumn.all();
        ArrayList<Descriptor<ListViewColumn>> left = new ArrayList<Descriptor<ListViewColumn>>(all);

        for (Class<? extends ListViewColumn> d: DEFAULT_COLUMNS) {
            Descriptor<ListViewColumn> des = all.find(d);
            if (des  != null) {
                try {
                    r.add(des.newInstance(null, null));
                    left.remove(des);
                } catch (FormException e) {
                    LOGGER.log(Level.WARNING, "Failed to instantiate "+des.clazz,e);
                }
            }
        }
        for (Descriptor<ListViewColumn> d : left)
            try {
                if (d instanceof ListViewColumnDescriptor) {
                    ListViewColumnDescriptor ld = (ListViewColumnDescriptor) d;
                    if (!ld.shownByDefault())       continue;   // skip this
                }
                ListViewColumn lvc = d.newInstance(null, null);
                if (!lvc.shownByDefault())      continue; // skip this

                r.add(lvc);
            } catch (FormException e) {
                LOGGER.log(Level.WARNING, "Failed to instantiate "+d.clazz,e);
            }

        columns = new DescribableList<ListViewColumn, Descriptor<ListViewColumn>>(this,r);
    }

    /**
     * Returns the transient {@link Action}s associated with the top page.
     *
     * @see Hudson#getActions()
     */
    @Override
    public List<Action> getActions() {
        return Hudson.getInstance().getActions();
    }

    public Iterable<ListViewColumn> getColumns() {
        return columns;
    }
    
    /**
     * Returns a read-only view of all {@link Job}s in this view.
     *
     * <p>
     * This method returns a separate copy each time to avoid
     * concurrent modification issue.
     */
    public synchronized List<TopLevelItem> getItems() {
        SortedSet<String> names = new TreeSet<String>(jobNames);

        if (includePattern != null) {
            for (TopLevelItem item : Hudson.getInstance().getItems()) {
                String itemName = item.getName();
                if (includePattern.matcher(itemName).matches()) {
                    names.add(itemName);
                }
            }
        }

        List<TopLevelItem> items = new ArrayList<TopLevelItem>(names.size());
        for (String n : names) {
            TopLevelItem item = Hudson.getInstance().getItem(n);
            if(item!=null)
                items.add(item);
        }
        return items;
    }

    public boolean contains(TopLevelItem item) {
        return jobNames.contains(item.getName());
    }

    public String getIncludeRegex() {
        return includeRegex;
    }

    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        Item item = Hudson.getInstance().doCreateItem(req, rsp);
        if(item!=null) {
            jobNames.add(item.getName());
            owner.save();
        }
        return item;
    }

    @Override
    public synchronized void onJobRenamed(Item item, String oldName, String newName) {
        if(jobNames.remove(oldName) && newName!=null)
            jobNames.add(newName);
    }

    /**
     * Handles the configuration submission.
     *
     * Load view-specific properties here.
     */
    @Override
    protected void submit(StaplerRequest req) throws ServletException, FormException {
        jobNames.clear();
        for (TopLevelItem item : Hudson.getInstance().getItems()) {
            if(req.getParameter(item.getName())!=null)
                jobNames.add(item.getName());
        }

        if (req.getParameter("useincluderegex") != null) {
            includeRegex = Util.nullify(req.getParameter("includeRegex"));
            if (includeRegex == null)
                includePattern = null;
            else
                includePattern = Pattern.compile(includeRegex);
        } else {
            includeRegex = null;
            includePattern = null;
        }

        if (columns == null) {
            columns = new DescribableList<ListViewColumn,Descriptor<ListViewColumn>>(Saveable.NOOP);
        }
        columns.rebuildHetero(req, req.getSubmittedForm(), ListViewColumn.all(), "columns");
    }

    @Extension
    public static final class DescriptorImpl extends ViewDescriptor {
        public String getDisplayName() {
            return Messages.ListView_DisplayName();
        }

        /**
         * Checks if the include regular expression is valid.
         */
        public FormValidation doCheckIncludeRegex( @QueryParameter String value ) throws IOException, ServletException, InterruptedException  {
            String v = Util.fixEmpty(value);
            if (v != null) {
                try {
                    Pattern.compile(v);
                } catch (PatternSyntaxException pse) {
                    return FormValidation.error(pse.getMessage());
                }
            }
            return FormValidation.ok();
        }
    }

    public static List<ListViewColumn> getDefaultColumns() {
        ArrayList<ListViewColumn> r = new ArrayList<ListViewColumn>();
        DescriptorExtensionList<ListViewColumn, Descriptor<ListViewColumn>> all = ListViewColumn.all();
        for (Class<? extends ListViewColumn> t : DEFAULT_COLUMNS) {
            Descriptor<ListViewColumn> d = all.find(t);
            if (d  != null) {
                try {
                    r.add (d.newInstance(null, null));
                } catch (FormException e) {
                    LOGGER.log(Level.WARNING, "Failed to instantiate "+d.clazz,e);
                }
            }
        }
        return Collections.unmodifiableList(r);
    }


    private static final Logger LOGGER = Logger.getLogger(ListView.class.getName());

    /**
     * Traditional column layout before the {@link ListViewColumn} becomes extensible.
     */
    private static final List<Class<? extends ListViewColumn>> DEFAULT_COLUMNS =  Arrays.asList(
        StatusColumn.class,
        WeatherColumn.class,
        JobColumn.class,
        LastSuccessColumn.class,
        LastFailureColumn.class,
        LastDurationColumn.class,
        BuildButtonColumn.class
    );
}
