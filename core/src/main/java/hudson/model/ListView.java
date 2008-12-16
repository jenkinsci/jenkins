package hudson.model;

import hudson.Util;
import hudson.util.CaseInsensitiveComparator;
import hudson.util.FormFieldValidator;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.text.ParseException;

/**
 * Displays {@link Job}s in a flat list view.
 *
 * @author Kohsuke Kawaguchi
 */
public class ListView extends View {

    private final Hudson owner;

    /**
     * List of job names. This is what gets serialized.
     */
    /*package*/ final Set<String> jobNames = new TreeSet<String>(CaseInsensitiveComparator.INSTANCE);

    /**
     * Name of this view.
     */
    private String name;

    /**
     * Message displayed in the view page.
     */
    private String description;

    /**
     * Include regex string.
     */
    private String includeRegex;
    
    /**
     * Compiled include pattern from the includeRegex string.
     */
    private transient Pattern includePattern;

    public ListView(Hudson owner, String name) {
        this.name = name;
        this.owner = owner;
    }

    /**
     * Returns the transient {@link Action}s associated with the top page.
     *
     * @see Hudson#getActions()
     /
    public List<Action> getActions() {
        return Hudson.getInstance().getActions();
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

        if (includeRegex != null) {
            try {
                if (includePattern == null) {
                    includePattern = Pattern.compile(includeRegex);
                }

                for (TopLevelItem item : owner.getItems()) {
                    String itemName = item.getName();
                    if (includePattern.matcher(itemName).matches()) {
                        names.add(itemName);
                    }
                }
            } catch (PatternSyntaxException pse) {
            }
        }

        List<TopLevelItem> items = new ArrayList<TopLevelItem>(names.size());
        for (String name : names) {
            TopLevelItem item = owner.getItem(name);
            if(item!=null)
                items.add(item);
        }
        return items;
    }

    public TopLevelItem getItem(String name) {
        return owner.getItem(name);
    }

    public TopLevelItem getJob(String name) {
        return getItem(name);
    }

    public boolean contains(TopLevelItem item) {
        return jobNames.contains(item.getName());
    }

    public String getViewName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getDisplayName() {
        return name;
    }
    
    public String getIncludeRegex() {
        return includeRegex;
    }

    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        Item item = owner.doCreateItem(req, rsp);
        if(item!=null) {
            jobNames.add(item.getName());
            owner.save();
        }
        return item;
    }

    public String getUrl() {
        return "view/"+name+'/';
    }

    /**
     * Accepts submission from the configuration page.
     */
    public synchronized void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(CONFIGURE);

        req.setCharacterEncoding("UTF-8");
        
        jobNames.clear();
        for (TopLevelItem item : owner.getItems()) {
            if(req.getParameter(item.getName())!=null)
                jobNames.add(item.getName());
        }

        description = Util.nullify(req.getParameter("description"));
        
        if (req.getParameter("useincluderegex") != null) {
            includeRegex = Util.nullify(req.getParameter("includeregex"));
        } else {
            includeRegex = null;
        }
        includePattern = null;

        try {
            String n = req.getParameter("name");
            Hudson.checkGoodName(n);
            name = n;
        } catch (ParseException e) {
            sendError(e, req, rsp);
            return;
        }

        owner.save();

        rsp.sendRedirect2("../"+name);
    }

    /**
     * Accepts the new description.
     */
    public synchronized void doSubmitDescription( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(CONFIGURE);

        req.setCharacterEncoding("UTF-8");
        description = req.getParameter("description");
        owner.save();
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * Deletes this view.
     */
    public synchronized void doDoDelete( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        checkPermission(DELETE);

        owner.deleteView(this);
        rsp.sendRedirect2(req.getContextPath()+"/");
    }

    /**
     * Checks if the include regular expression is valid.
     */
    public synchronized void doIncludeRegexCheck( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, InterruptedException  {
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
