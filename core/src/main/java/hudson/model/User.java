package hudson.model;

import com.thoughtworks.xstream.XStream;
import hudson.FeedAdapter;
import hudson.XmlFile;
import hudson.CopyOnWrite;
import hudson.model.Descriptor.FormException;
import hudson.scm.ChangeLogSet;
import hudson.util.RunList;
import hudson.util.XStream2;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a user.
 * 
 * @author Kohsuke Kawaguchi
 */
public class User extends AbstractModelObject {

    private transient final String id;

    private volatile String fullName;

    private volatile String description;

    /**
     * List of {@link UserProperty}s configured for this project.
     */
    @CopyOnWrite
    private volatile List<UserProperty> properties = new ArrayList<UserProperty>();


    private User(String id) {
        this.id = id;
        this.fullName = id;   // fullName defaults to name

        // load the other data from disk if it's available
        XmlFile config = getConfigFile();
        try {
            if(config.exists())
                config.unmarshal(this);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load "+config,e);
        }

        // allocate default instances if needed.
        // doing so after load makes sure that newly added user properties do get reflected
        for (UserPropertyDescriptor d : UserProperties.LIST) {
            if(getProperty(d.clazz)==null) {
                UserProperty up = d.newInstance(this);
                if(up!=null)
                    properties.add(up);
            }
        }

        for (UserProperty p : properties)
            p.setUser(this);
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return "user/"+ id;
    }

    /**
     * Gets the human readable name of this user.
     * This is configurable by the user.
     *
     * @return
     *      never null.
     */
    public String getFullName() {
        return fullName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Gets the user properties configured for this user.
     */
    public Map<Descriptor<UserProperty>,UserProperty> getProperties() {
        return Descriptor.toMap(properties);
    }

    /**
     * Gets the specific property, or null.
     */
    public <T extends UserProperty> T getProperty(Class<T> clazz) {
        for (UserProperty p : properties) {
            if(clazz.isInstance(p))
                return (T)p;    // can't use Class.cast as that's 5.0 feature
        }
        return null;
    }

    /**
     * Accepts the new description.
     */
    public synchronized void doSubmitDescription( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        req.setCharacterEncoding("UTF-8");

        description = req.getParameter("description");
        save();
        
        rsp.sendRedirect(".");  // go to the top page
    }



    public static User get(String name) {
        if(name==null)
            return null;
        synchronized(byName) {
            User u = byName.get(name);
            if(u==null) {
                u = new User(name);
                byName.put(name,u);
            }
            return u;
        }
    }

    /**
     * Returns the user name.
     */
    public String getDisplayName() {
        return getFullName();
    }

    /**
     * Gets the list of {@link Build}s that include changes by this user,
     * by the timestamp order.
     * 
     * TODO: do we need some index for this?
     */
    public List<Build> getBuilds() {
        List<Build> r = new ArrayList<Build>();
        for (Project p : Hudson.getInstance().getProjects()) {
            for (Build b : p.getBuilds()) {
                for (ChangeLogSet.Entry e : b.getChangeSet()) {
                    if(e.getAuthor()==this) {
                        r.add(b);
                        break;
                    }
                }
            }
        }
        Collections.sort(r,Run.ORDER_BY_DATE);
        return r;
    }

    public String toString() {
        return fullName;
    }

    /**
     * The file we save our configuration.
     */
    protected final XmlFile getConfigFile() {
        String safeId = id.replace('\\', '_').replace('/', '_');
        return new XmlFile(XSTREAM,new File(Hudson.getInstance().getRootDir(),"users/"+ safeId +"/config.xml"));
    }

    /**
     * Save the settings to a file.
     */
    public synchronized void save() throws IOException {
        XmlFile config = getConfigFile();
        config.mkdirs();
        config.write(this);
    }

    /**
     * Accepts submission from the configuration page.
     */
    public void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        req.setCharacterEncoding("UTF-8");

        try {
            fullName = req.getParameter("fullName");
            description = req.getParameter("description");

            List<UserProperty> props = new ArrayList<UserProperty>();
            for (Descriptor<UserProperty> d : UserProperties.LIST)
                props.add(d.newInstance(req));
            this.properties = props;

            save();

            rsp.sendRedirect(".");
        } catch (FormException e) {
            sendError(e,req,rsp);
        }
    }

    public void doRssAll( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rss(req, rsp, " all builds", RunList.fromRuns(getBuilds()));
    }

    public void doRssFailed( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rss(req, rsp, " regression builds", RunList.fromRuns(getBuilds()).regressionOnly());
    }

    private void rss(StaplerRequest req, StaplerResponse rsp, String suffix, RunList runs) throws IOException, ServletException {
        RSS.forwardToRss(getDisplayName()+ suffix, getUrl(),
            runs.newBuilds(), FEED_ADAPTER, req, rsp );
    }


    /**
     * Keyed by {@link User#id}.
     */
    private static final Map<String,User> byName = new HashMap<String,User>();

    /**
     * Used to load/save user configuration.
     */
    private static final XStream XSTREAM = new XStream2();

    private static final Logger LOGGER = Logger.getLogger(User.class.getName());

    static {
        XSTREAM.alias("user",User.class);
    }

    /**
     * {@link FeedAdapter} to produce build status summary in the feed.
     */
    public static final FeedAdapter<Run> FEED_ADAPTER = new FeedAdapter<Run>() {
        public String getEntryTitle(Run entry) {
            return entry+" : "+entry.getBuildStatusSummary().message;
        }

        public String getEntryUrl(Run entry) {
            return entry.getUrl();
        }

        public String getEntryID(Run entry) {
            return "tag:"+entry.getParent().getName()+':'+entry.getId();
        }

        public Calendar getEntryTimestamp(Run entry) {
            return entry.getTimestamp();
        }
    };
}
