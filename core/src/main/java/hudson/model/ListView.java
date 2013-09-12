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
import hudson.diagnosis.OldDataMonitor;
import hudson.model.Descriptor.FormException;
import hudson.util.CaseInsensitiveComparator;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import hudson.views.ListViewColumn;
import hudson.views.ViewJobFilter;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.annotation.concurrent.GuardedBy;
import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Displays {@link Job}s in a flat list view.
 *
 * @author Kohsuke Kawaguchi
 */
public class ListView extends View implements Saveable {

    /**
     * List of job names. This is what gets serialized.
     */
    @GuardedBy("this")
    /*package*/ /*almost-final*/ SortedSet<String> jobNames = new TreeSet<String>(CaseInsensitiveComparator.INSTANCE);
    
    private DescribableList<ViewJobFilter, Descriptor<ViewJobFilter>> jobFilters;

    private DescribableList<ListViewColumn, Descriptor<ListViewColumn>> columns;

    /**
     * Include regex string.
     */
    private String includeRegex;
    
    /**
     * Whether to recurse in ItemGroups
     */
    private boolean recurse;
    
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

    private Object readResolve() {
        if(includeRegex!=null) {
            try {
                includePattern = Pattern.compile(includeRegex);
            } catch (PatternSyntaxException x) {
                includeRegex = null;
                OldDataMonitor.report(this, Collections.<Throwable>singleton(x));
            }
        }
        if (jobNames == null) {
            jobNames = new TreeSet<String>(CaseInsensitiveComparator.INSTANCE);
        }
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

    public DescribableList<ListViewColumn, Descriptor<ListViewColumn>> getColumns() {
        return columns;
    }
    
    /**
     * Returns a read-only view of all {@link Job}s in this view.
     *
     * <p>
     * This method returns a separate copy each time to avoid
     * concurrent modification issue.
     */
    public List<TopLevelItem> getItems() {
        SortedSet<String> names;
        List<TopLevelItem> items = new ArrayList<TopLevelItem>();

        synchronized (this) {
            names = new TreeSet<String>(jobNames);
        }

        ItemGroup<? extends TopLevelItem> parent = getOwnerItemGroup();
        List<TopLevelItem> parentItems = new ArrayList<TopLevelItem>(parent.getItems());
        includeItems(parent, parentItems, names);

        Boolean statusFilter = this.statusFilter; // capture the value to isolate us from concurrent update
        for (TopLevelItem item : Items.getAllItems(getOwnerItemGroup(), TopLevelItem.class)) {
            if (!names.contains(item.getRelativeNameFrom(getOwnerItemGroup()))) continue;
            // Add if no status filter or filter matches enabled/disabled status:
            if(statusFilter == null || !(item instanceof AbstractProject)
                              || ((AbstractProject)item).isDisabled() ^ statusFilter)
                items.add(item);
        }

        // check the filters
        Iterable<ViewJobFilter> jobFilters = getJobFilters();
        List<TopLevelItem> allItems = new ArrayList<TopLevelItem>(parentItems);
    	for (ViewJobFilter jobFilter: jobFilters) {
    		items = jobFilter.filter(items, allItems, this);
    	}
        // for sanity, trim off duplicates
        items = new ArrayList<TopLevelItem>(new LinkedHashSet<TopLevelItem>(items));
        
        return items;
    }
    
    @Override
    public boolean contains(TopLevelItem item) {
      return getItems().contains(item);
    }
    
    private void includeItems(ItemGroup<? extends TopLevelItem> root, Collection<? extends Item> parentItems, SortedSet<String> names) {
        if (includePattern != null) {
            for (Item item : parentItems) {
                if (recurse && item instanceof ItemGroup) {
                    ItemGroup<?> ig = (ItemGroup<?>) item;
                    includeItems(root, ig.getItems(), names);
                }
                if (item instanceof TopLevelItem) {
                    String itemName = item.getRelativeNameFrom(root);
                    if (includePattern.matcher(itemName).matches()) {
                        names.add(itemName);
                    }
                }
            }
        }
    }
    
    public synchronized boolean jobNamesContains(TopLevelItem item) {
        if (item == null) return false;
        return jobNames.contains(item.getRelativeNameFrom(getOwnerItemGroup()));
    }

    

    /**
     * Adds the given item to this view.
     *
     * @since 1.389
     */
    public void add(TopLevelItem item) throws IOException {
        synchronized (this) {
            jobNames.add(item.getRelativeNameFrom(getOwnerItemGroup()));
        }
        save();
    }

    public String getIncludeRegex() {
        return includeRegex;
    }
    
    public boolean isRecurse() {
        return recurse;
    }
    
    /*
     * For testing purposes
     */
    void setRecurse(boolean recurse) {
        this.recurse = recurse;
    }

    /**
     * Filter by enabled/disabled status of jobs.
     * Null for no filter, true for enabled-only, false for disabled-only.
     */
    public Boolean getStatusFilter() {
        return statusFilter;
    }

    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        ItemGroup<? extends TopLevelItem> ig = getOwnerItemGroup();
        if (ig instanceof ModifiableItemGroup) {
            TopLevelItem item = ((ModifiableItemGroup<? extends TopLevelItem>)ig).doCreateItem(req, rsp);
            if(item!=null) {
                synchronized (this) {
                    jobNames.add(item.getRelativeNameFrom(getOwnerItemGroup()));
                }
                owner.save();
            }
            return item;
        }
        return null;
    }

    @RequirePOST
    public HttpResponse doAddJobToView(@QueryParameter String name) throws IOException, ServletException {
        checkPermission(View.CONFIGURE);
        if(name==null)
            throw new Failure("Query parameter 'name' is required");

        if (getOwnerItemGroup().getItem(name) == null)
            throw new Failure("Query parameter 'name' does not correspond to a known item");

        if (jobNames.add(name))
            owner.save();

        return HttpResponses.ok();
    }

    @RequirePOST
    public HttpResponse doRemoveJobFromView(@QueryParameter String name) throws IOException, ServletException {
        checkPermission(View.CONFIGURE);
        if(name==null)
            throw new Failure("Query parameter 'name' is required");

        if (jobNames.remove(name))
            owner.save();

        return HttpResponses.ok();
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
        JSONObject json = req.getSubmittedForm();
        synchronized (this) {
            recurse = json.optBoolean("recurse", true);
            jobNames.clear();
            Iterable<? extends TopLevelItem> items;
            if (recurse) {
                items = Items.getAllItems(getOwnerItemGroup(), TopLevelItem.class);
            } else {
                items = getOwnerItemGroup().getItems();
            }
            for (TopLevelItem item : items) {
                String relativeNameFrom = item.getRelativeNameFrom(getOwnerItemGroup());
                if(req.getParameter(relativeNameFrom)!=null) {
                    jobNames.add(relativeNameFrom);
                }
            }
        }

        setIncludeRegex(req.getParameter("useincluderegex") != null ? req.getParameter("includeRegex") : null);

        if (columns == null) {
            columns = new DescribableList<ListViewColumn,Descriptor<ListViewColumn>>(this);
        }
        columns.rebuildHetero(req, json, ListViewColumn.all(), "columns");
        
        if (jobFilters == null) {
        	jobFilters = new DescribableList<ViewJobFilter,Descriptor<ViewJobFilter>>(this);
        }
        jobFilters.rebuildHetero(req, json, ViewJobFilter.all(), "jobFilters");

        String filter = Util.fixEmpty(req.getParameter("statusFilter"));
        statusFilter = filter != null ? "1".equals(filter) : null;
    }
    
    public void setIncludeRegex(String includeRegex) {
        this.includeRegex = Util.nullify(includeRegex);
        if (this.includeRegex == null)
            this.includePattern = null;
        else
            this.includePattern = Pattern.compile(includeRegex);
    }

    @Extension
    public static class DescriptorImpl extends ViewDescriptor {
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
