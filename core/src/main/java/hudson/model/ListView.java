/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Erik Ramfelt, Seiji Sogabe, Martin Eigenbrodt, Alan Harder
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

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor.FormException;
import hudson.util.CaseInsensitiveComparator;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.views.ListViewColumn;
import hudson.views.ViewJobFilter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
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
    
    private DescribableList<ViewJobFilter, Descriptor<ViewJobFilter>> jobFilters;

    private DescribableList<ListViewColumn, Descriptor<ListViewColumn>> columns;

    /**
     * Include regex string.
     */
    private String includeRegex;
    
    /**
     * Compiled include pattern from the includeRegex string.
     */
    private transient Pattern includePattern;

    /**
     * Filter by enabled/disabled status of jobs.
     * Null for no filter, true for enabled-only, false for disabled-only.
     */
    private Boolean statusFilter;

    @DataBoundConstructor
    public ListView(String name) {
        super(name);
        initColumns();
        initJobFilters();
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
        initJobFilters();
        return this;
    }

    protected void initColumns() {
        if (columns == null)
            columns = new DescribableList<ListViewColumn, Descriptor<ListViewColumn>>(this,ListViewColumn.createDefaultInitialColumnList());
    }

    protected void initJobFilters() {
        if (jobFilters == null)
            jobFilters = new DescribableList<ViewJobFilter, Descriptor<ViewJobFilter>>(this);
    }

    /**
     * Used to determine if we want to display the Add button.
     */
    public boolean hasJobFilterExtensions() {
    	return !ViewJobFilter.all().isEmpty();
    }

    public DescribableList<ViewJobFilter, Descriptor<ViewJobFilter>> getJobFilters() {
    	return jobFilters;
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
            // Add if no status filter or filter matches enabled/disabled status:
            if(item!=null && (statusFilter == null || !(item instanceof AbstractProject)
                              || ((AbstractProject)item).isDisabled() ^ statusFilter))
                items.add(item);
        }

        // check the filters
        Iterable<ViewJobFilter> jobFilters = getJobFilters();
        List<TopLevelItem> allItems = Hudson.getInstance().getItems();
    	for (ViewJobFilter jobFilter: jobFilters) {
    		items = jobFilter.filter(items, allItems, this);
    	}
        // for sanity, trim off duplicates
        items = new ArrayList<TopLevelItem>(new LinkedHashSet<TopLevelItem>(items));
        
        return items;
    }

    public boolean contains(TopLevelItem item) {
        return jobNames.contains(item.getName());
    }

    /**
     * Adds the given item to this view.
     *
     * @since 1.389
     */
    public void add(TopLevelItem item) throws IOException {
        jobNames.add(item.getName());
        save();
    }

    public String getIncludeRegex() {
        return includeRegex;
    }

    /**
     * Filter by enabled/disabled status of jobs.
     * Null for no filter, true for enabled-only, false for disabled-only.
     */
    public Boolean getStatusFilter() {
        return statusFilter;
    }

    public synchronized Item doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
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
    protected void submit(StaplerRequest req) throws ServletException, FormException, IOException {
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
            columns = new DescribableList<ListViewColumn,Descriptor<ListViewColumn>>(this);
        }
        columns.rebuildHetero(req, req.getSubmittedForm(), ListViewColumn.all(), "columns");
        
        if (jobFilters == null) {
        	jobFilters = new DescribableList<ViewJobFilter,Descriptor<ViewJobFilter>>(this);
        }
        jobFilters.rebuildHetero(req, req.getSubmittedForm(), ViewJobFilter.all(), "jobFilters");

        String filter = Util.fixEmpty(req.getParameter("statusFilter"));
        statusFilter = filter != null ? "1".equals(filter) : null;
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

    /**
     * @deprecated as of 1.391
     *  Use {@link ListViewColumn#createDefaultInitialColumnList()}
     */
    public static List<ListViewColumn> getDefaultColumns() {
        return ListViewColumn.createDefaultInitialColumnList();
    }
}
