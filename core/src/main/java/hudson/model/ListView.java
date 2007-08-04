package hudson.model;

import hudson.Util;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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
    /*package*/ final Set<String> jobNames = new TreeSet<String>();

    /**
     * Name of this view.
     */
    private String name;

    /**
     * Message displayed in the view page.
     */
    private String description;


    public ListView(Hudson owner, String name) {
        this.name = name;
        this.owner = owner;
    }

    /**
     * Returns a read-only view of all {@link Job}s in this view.
     *
     * <p>
     * This method returns a separate copy each time to avoid
     * concurrent modification issue.
     */
    public synchronized List<TopLevelItem> getItems() {
        TopLevelItem[] items = new TopLevelItem[jobNames.size()];
        int i=0;
        for (String name : jobNames)
            items[i++] = owner.getItem(name);
        return Arrays.asList(items);
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

    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return null;

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
    public synchronized void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        req.setCharacterEncoding("UTF-8");
        
        jobNames.clear();
        for (TopLevelItem item : owner.getItems()) {
            if(req.getParameter(item.getName())!=null)
                jobNames.add(item.getName());
        }

        description = Util.nullify(req.getParameter("description"));

        name = req.getParameter("name");

        owner.save();

        rsp.sendRedirect("../"+ req.getParameter("name"));
    }

    /**
     * Accepts the new description.
     */
    public synchronized void doSubmitDescription( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        req.setCharacterEncoding("UTF-8");
        description = req.getParameter("description");
        owner.save();
        rsp.sendRedirect(".");  // go to the top page
    }

    /**
     * Deletes this view.
     */
    public synchronized void doDoDelete( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        owner.deleteView(this);
        rsp.sendRedirect2(req.getContextPath()+"/");
    }
}
