/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt, Seiji Sogabe
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

import hudson.Util;
import hudson.views.ListViewColumn;
import hudson.model.Descriptor.FormException;
import hudson.util.CaseInsensitiveComparator;
import hudson.util.FormFieldValidator;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Displays {@link Job}s in a flat list view.
 *
 * @author Kohsuke Kawaguchi
 */
public class ListView extends View {
    /**
     * List of job names. This is what gets serialized.
     */
    /*package*/ final Set<String> jobNames = new TreeSet<String>(CaseInsensitiveComparator.INSTANCE);

    private transient List<ListViewColumn> columns = new ArrayList<ListViewColumn>();

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

    private Object readResolve() {
        if(includeRegex!=null)
            includePattern = Pattern.compile(includeRegex);
        initColumns();
        return this;
    }

    private void initColumns() {
        // create all instances
        ArrayList<ListViewColumn> r = new ArrayList<ListViewColumn>();
        for (Descriptor<ListViewColumn> d : ListViewColumn.LIST)
            try {
                r.add(d.newInstance(null,null));
            } catch (FormException e) {
                // so far impossible. TODO: report
            }
        columns = Collections.unmodifiableList(r);
    }

    /**
     * Returns the transient {@link Action}s associated with the top page.
     *
     * @see Hudson#getActions()
     */
    public List<Action> getActions() {
        return Hudson.getInstance().getActions();
    }

    public List<ListViewColumn> getColumns() {
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
        Set<String> names = (Set<String>) ((TreeSet<String>) jobNames).clone();

        if (includePattern != null) {
            for (TopLevelItem item : Hudson.getInstance().getItems()) {
                String itemName = item.getName();
                if (includePattern.matcher(itemName).matches()) {
                    names.add(itemName);
                }
            }
        }

        List<TopLevelItem> items = new ArrayList<TopLevelItem>(names.size());
        for (String name : names) {
            TopLevelItem item = Hudson.getInstance().getItem(name);
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
    protected void submit(StaplerRequest req) {
        jobNames.clear();
        for (TopLevelItem item : Hudson.getInstance().getItems()) {
            if(req.getParameter(item.getName())!=null)
                jobNames.add(item.getName());
        }

        if (req.getParameter("useincluderegex") != null) {
            includeRegex = Util.nullify(req.getParameter("includeRegex"));
            includePattern = Pattern.compile(includeRegex);
        } else {
            includeRegex = null;
            includePattern = null;
        }
    }

    public ViewDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    static {
        LIST.add(DESCRIPTOR);
    }

    public static final class DescriptorImpl extends ViewDescriptor {
        private DescriptorImpl() {
            super(ListView.class);
        }

        public String getDisplayName() {
            return Messages.ListView_DisplayName();
        }

        /**
         * Checks if the include regular expression is valid.
         */
        public void doCheckIncludeRegex( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, InterruptedException  {
            new FormFieldValidator(req, rsp, false) {
                @Override
                protected void check() throws IOException, ServletException {
                    String v = Util.fixEmpty(request.getParameter("value"));
                    if (v != null) {
                        try {
                            Pattern.compile(v);
                        } catch (PatternSyntaxException pse) {
                            error(pse.getMessage());
                        }
                    }
                    ok();
                }
            }.process();
        }
    }
}
