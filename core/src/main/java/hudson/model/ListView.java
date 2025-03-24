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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.Descriptor.FormException;
import hudson.model.listeners.ItemListener;
import hudson.search.SearchIndexBuilder;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import hudson.views.ListViewColumn;
import hudson.views.StatusFilter;
import hudson.views.ViewJobFilter;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jenkins.model.Jenkins;
import net.jcip.annotations.GuardedBy;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;
import org.springframework.security.access.AccessDeniedException;

/**
 * Displays {@link Job}s in a flat list view.
 *
 * @author Kohsuke Kawaguchi
 */
public class ListView extends View implements DirectlyModifiableView {

    /**
     * List of job names. This is what gets serialized.
     */
    @GuardedBy("this")
    /*package*/ /*almost-final*/ SortedSet<String> jobNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    private DescribableList<ViewJobFilter, Descriptor<ViewJobFilter>> jobFilters;

    private DescribableList<ListViewColumn, Descriptor<ListViewColumn>> columns;

    /**
     * Include regex string.
     */
    private String includeRegex;

    /**
     * Whether to recurse in ItemGroups
     */
    private volatile boolean recurse;

    /**
     * Compiled include pattern from the includeRegex string.
     */
    private transient Pattern includePattern;

    /**
     * Filter by enabled/disabled status of jobs.
     * Null for no filter, true for enabled-only, false for disabled-only.
     * @deprecated Status filter is now controlled via a {@link ViewJobFilter}, see {@link StatusFilter}
     */
    @Deprecated
    private transient Boolean statusFilter;

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

    /**
     * Sets the columns of this view.
     */
    @DataBoundSetter
    public void setColumns(List<ListViewColumn> columns) throws IOException {
        this.columns.replaceBy(columns);
    }

    @DataBoundSetter
    public void setJobFilters(List<ViewJobFilter> jobFilters) throws IOException {
        this.jobFilters.replaceBy(jobFilters);
    }

    protected Object readResolve() {
        if (includeRegex != null) {
            try {
                includePattern = Pattern.compile(includeRegex);
            } catch (PatternSyntaxException x) {
                includeRegex = null;
                OldDataMonitor.report(this, Set.of(x));
            }
        }
        synchronized (this) {
            if (jobNames == null) {
                jobNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            }
        }
        initColumns();
        initJobFilters();
        if (statusFilter != null) {
            jobFilters.add(new StatusFilter(statusFilter));
        }
        return this;
    }

    protected void initColumns() {
        if (columns == null)
            columns = new DescribableList<>(this,
                    ListViewColumn.createDefaultInitialColumnList(getClass())
            );
    }

    protected void initJobFilters() {
        if (jobFilters == null)
            jobFilters = new DescribableList<>(this);
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

    @Override
    public DescribableList<ListViewColumn, Descriptor<ListViewColumn>> getColumns() {
        return columns;
    }

    public synchronized Set<String> getJobNames() {
        return Collections.unmodifiableSet(jobNames);
    }

    /**
     * Returns a read-only view of all {@link Job}s in this view.
     *
     * <p>
     * This method returns a separate copy each time to avoid
     * concurrent modification issue.
     */
    @Override
    public List<TopLevelItem> getItems() {
        return getItems(this.recurse);
     }

    /**
     * Returns a read-only view of all {@link Job}s in this view.
     *
     *
     * <p>
     * This method returns a separate copy each time to avoid
     * concurrent modification issue.
     * @param recurse {@code false} not to recurse in ItemGroups
     * true to recurse in ItemGroups
     */
    private List<TopLevelItem> getItems(boolean recurse) {
        SortedSet<String> names;
        List<TopLevelItem> items = new ArrayList<>();

        synchronized (this) {
            names = new TreeSet<>(jobNames);
        }

        ItemGroup<? extends TopLevelItem> parent = getOwner().getItemGroup();

        if (recurse) {
            if (!names.isEmpty() || includePattern != null) {
                items.addAll(parent.getAllItems(TopLevelItem.class, item -> {
                    String itemName = item.getRelativeNameFrom(parent);
                    if (names.contains(itemName)) {
                        return true;
                    }
                    if (includePattern != null) {
                        return includePattern.matcher(itemName).matches();
                    }
                    return false;
                }));
            }
        } else {
            for (String name : names) {
                try {
                    TopLevelItem i = parent.getItem(name);
                    if (i != null) {
                        items.add(i);
                    }
                } catch (AccessDeniedException e) {
                    //Ignore
                }
            }
            if (includePattern != null) {
                items.addAll(parent.getItems(item -> {
                    String itemName = item.getRelativeNameFrom(parent);
                    return includePattern.matcher(itemName).matches();
                }));
            }
        }

        Collection<ViewJobFilter> jobFilters = getJobFilters();
        if (!jobFilters.isEmpty()) {
            List<TopLevelItem> candidates = recurse ? parent.getAllItems(TopLevelItem.class) : new ArrayList<>(parent.getItems());
            // check the filters
            for (ViewJobFilter jobFilter : jobFilters) {
                items = jobFilter.filter(items, candidates, this);
            }
        }
        // for sanity, trim off duplicates
        items = new ArrayList<>(new LinkedHashSet<>(items));

        return items;
    }

    @Override
    public SearchIndexBuilder makeSearchIndex() {
        SearchIndexBuilder sib = new SearchIndexBuilder().addAllAnnotations(this);
        makeSearchIndex(sib);
        // add the display name for each item in the search index
        addDisplayNamesToSearchIndex(sib, getItems(true));
        return sib;
    }

    @Override
    public boolean contains(TopLevelItem item) {
      return getItems().contains(item);
    }

    public synchronized boolean jobNamesContains(TopLevelItem item) {
        if (item == null) return false;
        return jobNames.contains(item.getRelativeNameFrom(getOwner().getItemGroup()));
    }

    /**
     * Adds the given item to this view.
     *
     * @since 1.389
     */
    @Override
    public void add(TopLevelItem item) throws IOException {
        synchronized (this) {
            jobNames.add(item.getRelativeNameFrom(getOwner().getItemGroup()));
        }
        save();
    }

    /**
     * Removes given item from this view.
     *
     * @since 1.566
     */
    @Override
    public boolean remove(TopLevelItem item) throws IOException {
        synchronized (this) {
            String name = item.getRelativeNameFrom(getOwner().getItemGroup());
            if (!jobNames.remove(name)) return false;
        }
        save();
        return true;
    }

    public String getIncludeRegex() {
        return includeRegex;
    }

    public boolean isRecurse() {
        return recurse;
    }

    /**
     * @since 1.568
     */
    @DataBoundSetter
    public void setRecurse(boolean recurse) {
        this.recurse = recurse;
    }

    /**
     * Filter by enabled/disabled status of jobs.
     * Null for no filter, true for enabled-only, false for disabled-only.
     * @deprecated Status filter is now controlled via a {@link ViewJobFilter}, see {@link StatusFilter}
     */
    @Deprecated
    public Boolean getStatusFilter() {
        return statusFilter;
    }

    /**
     * Determines the initial state of the checkbox.
     *
     * @return true when the view is empty or already contains jobs specified by name.
     */
    @Restricted(NoExternalUse.class) // called from newJob_button-bar view
    @SuppressWarnings("unused") // called from newJob_button-bar view
    public boolean isAddToCurrentView() {
        synchronized (this) {
            return !jobNames.isEmpty() || // There are already items in this view specified by name
                    (jobFilters.isEmpty() && includePattern == null) // No other way to include items is used
                    ;
        }
    }

    private boolean needToAddToCurrentView(StaplerRequest2 req) throws ServletException {
        String json = req.getParameter("json");
        if (json != null && !json.isEmpty()) {
            // Submitted via UI
            JSONObject form = req.getSubmittedForm();
            return form.has("addToCurrentView") && form.getBoolean("addToCurrentView");
        } else {
            // Submitted via API
            return true;
        }
    }

    @Override
    @POST
    public Item doCreateItem(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        ItemGroup<? extends TopLevelItem> ig = getOwner().getItemGroup();
        if (ig instanceof ModifiableItemGroup) {
            TopLevelItem item = ((ModifiableItemGroup<? extends TopLevelItem>) ig).doCreateItem(req, rsp);
            if (item != null) {
                if (needToAddToCurrentView(req)) {
                    synchronized (this) {
                        jobNames.add(item.getRelativeNameFrom(getOwner().getItemGroup()));
                    }
                    owner.save();
                }
            }
            return item;
        }
        return null;
    }

    @Override
    @RequirePOST
    public HttpResponse doAddJobToView(@QueryParameter String name) throws IOException, ServletException {
        checkPermission(View.CONFIGURE);
        if (name == null)
            throw new Failure("Query parameter 'name' is required");

        TopLevelItem item = resolveName(name);
        if (item == null)
            throw new Failure("Query parameter 'name' does not correspond to a known item");

        if (contains(item)) return HttpResponses.ok();

        add(item);
        owner.save();

        return HttpResponses.ok();
    }

    @Override
    @RequirePOST
    public HttpResponse doRemoveJobFromView(@QueryParameter String name) throws IOException, ServletException {
        checkPermission(View.CONFIGURE);
        if (name == null)
            throw new Failure("Query parameter 'name' is required");

        TopLevelItem item = resolveName(name);
        if (item == null)
            throw new Failure("Query parameter 'name' does not correspond to a known and readable item");

        if (remove(item))
            owner.save();

        return HttpResponses.ok();
    }

    private @CheckForNull TopLevelItem resolveName(String name) {
        TopLevelItem item = getOwner().getItemGroup().getItem(name);
        if (item == null) {
            name = Items.getCanonicalName(getOwner().getItemGroup(), name);
            item = Jenkins.get().getItemByFullName(name, TopLevelItem.class);
        }
        return item;
    }

    /**
     * Handles the configuration submission.
     *
     * Load view-specific properties here.
     */
    @Override
    protected void submit(StaplerRequest2 req) throws ServletException, FormException, IOException {
        JSONObject json = req.getSubmittedForm();
        synchronized (this) {
            recurse = json.optBoolean("recurse", true);
            jobNames.clear();
            Iterable<? extends TopLevelItem> items;
            if (recurse) {
                items = getOwner().getItemGroup().getAllItems(TopLevelItem.class);
            } else {
                items = getOwner().getItemGroup().getItems();
            }
            for (TopLevelItem item : items) {
                String relativeNameFrom = item.getRelativeNameFrom(getOwner().getItemGroup());
                if (req.getParameter("item_" + relativeNameFrom) != null) {
                    jobNames.add(relativeNameFrom);
                }
            }
        }

        setIncludeRegex(req.getParameter("useincluderegex") != null ? req.getParameter("includeRegex") : null);

        if (columns == null) {
            columns = new DescribableList<>(this);
        }
        columns.rebuildHetero(req, json, ListViewColumn.all(), "columns");

        if (jobFilters == null) {
            jobFilters = new DescribableList<>(this);
        }
        jobFilters.rebuildHetero(req, json, ViewJobFilter.all(), "jobFilters");

        String filter = Util.fixEmpty(req.getParameter("statusFilter"));
        statusFilter = filter != null ? "1".equals(filter) : null;
    }

    /** @since 1.526 */
    @DataBoundSetter
    public void setIncludeRegex(String includeRegex) {
        this.includeRegex = Util.nullify(includeRegex);
        if (this.includeRegex == null)
            this.includePattern = null;
        else
            this.includePattern = Pattern.compile(includeRegex);
    }

    @DataBoundSetter
    public synchronized void setJobNames(Set<String> jobNames) {
        this.jobNames = new TreeSet<>(jobNames);
    }

    /**
     * @deprecated Status filter is now controlled via a {@link ViewJobFilter}, see {@link StatusFilter}
     */
    @Deprecated
    @DataBoundSetter
    public void setStatusFilter(Boolean statusFilter) {
        this.statusFilter = statusFilter;
    }

    @Extension @Symbol("list")
    public static class DescriptorImpl extends ViewDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.ListView_DisplayName();
        }

        /**
         * Checks if the include regular expression is valid.
         */
        public FormValidation doCheckIncludeRegex(@QueryParameter String value) throws IOException, ServletException, InterruptedException  {
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
    @Deprecated
    public static List<ListViewColumn> getDefaultColumns() {
        return ListViewColumn.createDefaultInitialColumnList(ListView.class);
    }

    @Restricted(NoExternalUse.class)
    @Extension
    public static final class Listener extends ItemListener {
        @Override
        public void onLocationChanged(final Item item, final String oldFullName, final String newFullName) {
            try (ACLContext acl = ACL.as2(ACL.SYSTEM2)) {
                locationChanged(oldFullName, newFullName);
            }
        }

        private void locationChanged(String oldFullName, String newFullName) {
            final Jenkins jenkins = Jenkins.get();
            locationChanged(jenkins, oldFullName, newFullName);
            for (Item g : jenkins.allItems()) {
                if (g instanceof ViewGroup) {
                    locationChanged((ViewGroup) g, oldFullName, newFullName);
                }
            }
        }

        private void locationChanged(ViewGroup vg, String oldFullName, String newFullName) {
            for (View v : vg.getViews()) {
                if (v instanceof ListView) {
                    renameViewItem(oldFullName, newFullName, vg, (ListView) v);
                }
                if (v instanceof ViewGroup) {
                    locationChanged((ViewGroup) v, oldFullName, newFullName);
                }
            }
        }

        private void renameViewItem(String oldFullName, String newFullName, ViewGroup vg, ListView lv) {
            boolean needsSave;
            synchronized (lv) {
                Set<String> oldJobNames = new HashSet<>(lv.jobNames);
                lv.jobNames.clear();
                for (String oldName : oldJobNames) {
                    lv.jobNames.add(Items.computeRelativeNamesAfterRenaming(oldFullName, newFullName, oldName, vg.getItemGroup()));
                }
                needsSave = !oldJobNames.equals(lv.jobNames);
            }
            if (needsSave) { // do not hold ListView lock at the time
                try {
                    lv.save();
                } catch (IOException x) {
                    Logger.getLogger(ListView.class.getName()).log(Level.WARNING, null, x);
                }
            }
        }

        @Override
        public void onDeleted(final Item item) {
            try (ACLContext acl = ACL.as2(ACL.SYSTEM2)) {
                deleted(item);
            }
        }

        private void deleted(Item item) {
            final Jenkins jenkins = Jenkins.get();
            deleted(jenkins, item);
            for (Item g : jenkins.allItems()) {
                if (g instanceof ViewGroup) {
                    deleted((ViewGroup) g, item);
                }
            }
        }

        private void deleted(ViewGroup vg, Item item) {
            for (View v : vg.getViews()) {
                if (v instanceof ListView) {
                    deleteViewItem(item, vg, (ListView) v);
                }
                if (v instanceof ViewGroup) {
                    deleted((ViewGroup) v, item);
                }
            }
        }

        private void deleteViewItem(Item item, ViewGroup vg, ListView lv) {
            boolean needsSave;
            synchronized (lv) {
                needsSave = lv.jobNames.remove(item.getRelativeNameFrom(vg.getItemGroup()));
            }
            if (needsSave) {
                try {
                    lv.save();
                } catch (IOException x) {
                    Logger.getLogger(ListView.class.getName()).log(Level.WARNING, null, x);
                }
            }
        }
    }
}
