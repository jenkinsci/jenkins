package hudson.model;

import com.thoughtworks.xstream.XStream;
import groovy.lang.GroovyShell;
import hudson.BasicAuthenticationFilter;
import hudson.FeedAdapter;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Launcher.LocalLauncher;
import hudson.Plugin;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.TcpSlaveAgentListener;
import hudson.Util;
import static hudson.Util.fixEmpty;
import hudson.XmlFile;
import hudson.model.Descriptor.FormException;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.JobListener;
import hudson.model.listeners.JobListener.JobListenerAdapter;
import hudson.model.listeners.SCMListener;
import hudson.remoting.LocalChannel;
import hudson.remoting.VirtualChannel;
import hudson.scm.CVSSCM;
import hudson.scm.RepositoryBrowser;
import hudson.scm.RepositoryBrowsers;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMS;
import hudson.search.CollectionSearchIndex;
import hudson.search.SearchIndexBuilder;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Builder;
import hudson.tasks.DynamicLabeler;
import hudson.tasks.LabelFinder;
import hudson.tasks.Mailer;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.triggers.Triggers;
import hudson.util.*;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Root object of the system.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Hudson extends View implements ItemGroup<TopLevelItem>, Node {
    private transient final Queue queue = new Queue();

    /**
     * {@link Computer}s in this Hudson system. Read-only.
     */
    private transient final Map<Node,Computer> computers = new CopyOnWriteMap.Hash<Node,Computer>();

    /**
     * Number of executors of the master node.
     */
    private int numExecutors = 2;

    /**
     * False to enable anyone to do anything.
     */
    private boolean useSecurity = false;

    /**
     * Message displayed in the top page.
     */
    private String systemMessage;

    /**
     * Root directory of the system.
     */
    public transient final File root;

    /**
     * All {@link Item}s keyed by their {@link Item#getName() name}s.
     */
    /*package*/ transient final Map<String,TopLevelItem> items = new CopyOnWriteMap.Tree<String,TopLevelItem>();

    /**
     * The sole instance.
     */
    private static Hudson theInstance;

    private transient boolean isQuietingDown;
    private transient boolean terminating;

    private List<JDK> jdks = new ArrayList<JDK>();

    private transient volatile DependencyGraph dependencyGraph = DependencyGraph.EMPTY;

    /**
     * Set of installed cluster nodes.
     *
     * We use this field with copy-on-write semantics.
     * This field has mutable list (to keep the serialization look clean),
     * but it shall never be modified. Only new completely populated slave
     * list can be set here.
     */
    private volatile List<Slave> slaves;

    /**
     * Quiet period.
     *
     * This is {@link Integer} so that we can initialize it to '5' for upgrading users.
     */
    /*package*/ Integer quietPeriod;

    /**
     * {@link ListView}s.
     */
    private List<ListView> views;   // can't initialize it eagerly for backward compatibility

    private transient final FingerprintMap fingerprintMap = new FingerprintMap();

    /**
     * Loaded plugins.
     */
    public transient final PluginManager pluginManager;

    public transient volatile TcpSlaveAgentListener tcpSlaveAgentListener;

    /**
     * List of registered {@link JobListener}s.
     */
    private transient final CopyOnWriteList<ItemListener> viewItemListeners = new CopyOnWriteList<ItemListener>();

    /**
     * List of registered {@link SCMListener}s.
     */
    private transient final CopyOnWriteList<SCMListener> scmListeners = new CopyOnWriteList<SCMListener>();

    /**
     * TCP slave agent port.
     * 0 for random, -1 to disable.
     */
    private int slaveAgentPort =0;

    /**
     * Once plugin is uploaded, this flag becomes true.
     * This is used to report a message that Hudson needs to be restarted
     * for new plugins to take effect.
     */
    private transient boolean pluginUploaded =false;

    /**
     * All labels known to Hudson. This allows us to reuse the same label instances
     * as much as possible, even though that's not a strict requirement.
     */
    private transient final ConcurrentHashMap<String,Label> labels = new ConcurrentHashMap<String,Label>();
    private transient Set<Label> labelSet;
    private transient Set<Label> dynamicLabels = null;

    public static Hudson getInstance() {
        return theInstance;
    }


    public Hudson(File root, ServletContext context) throws IOException {
        this.root = root;
        if(theInstance!=null)
            throw new IllegalStateException("second instance");
        theInstance = this;

        // load plugins.
        pluginManager = new PluginManager(context);

        if(slaveAgentPort!=-1)
            tcpSlaveAgentListener = new TcpSlaveAgentListener(slaveAgentPort);
        else
            tcpSlaveAgentListener = null;

        // if we are loading old data that doesn't have this field
        if(slaves==null)    slaves = new ArrayList<Slave>();

        // work around to have MavenModule register itself until we either move it to a plugin
        // or make it a part of the core.
        Items.LIST.hashCode();

        load();
        updateComputerList();

        getQueue().load();

        for (ItemListener l : viewItemListeners)
            l.onLoaded();
    }

    public TcpSlaveAgentListener getTcpSlaveAgentListener() {
        return tcpSlaveAgentListener;
    }

    @Exported
    public int getSlaveAgentPort() {
        return slaveAgentPort;
    }

    /**
     * If you are calling this on Hudson something is wrong.
     *
     * @deprecated
     */
    @Deprecated
    public String getNodeName() {
        return "";
    }

    public String getNodeDescription() {
        return "the master Hudson node";
    }

    public String getDescription() {
        return systemMessage;
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    /**
     * Gets the SCM descriptor by name. Primarily used for making them web-visible.
     */
    public Descriptor<SCM> getScm(String shortClassName) {
        return findDescriptor(shortClassName,SCMS.SCMS);
    }

    /**
     * Gets the repository browser descriptor by name. Primarily used for making them web-visible.
     */
    public Descriptor<RepositoryBrowser<?>> getRepositoryBrowser(String shortClassName) {
        return findDescriptor(shortClassName,RepositoryBrowsers.LIST);
    }

    /**
     * Gets the builder descriptor by name. Primarily used for making them web-visible.
     */
    public Descriptor<Builder> getBuilder(String shortClassName) {
        return findDescriptor(shortClassName, BuildStep.BUILDERS);
    }

    /**
     * Gets the build wrapper descriptor by name. Primarily used for making them web-visible.
     */
    public Descriptor<BuildWrapper> getBuildWrapper(String shortClassName) {
        return findDescriptor(shortClassName, BuildWrappers.WRAPPERS);
    }

    /**
     * Gets the publisher descriptor by name. Primarily used for making them web-visible.
     */
    public Descriptor<Publisher> getPublisher(String shortClassName) {
        return findDescriptor(shortClassName, BuildStep.PUBLISHERS);
    }

    /**
     * Gets the trigger descriptor by name. Primarily used for making them web-visible.
     */
    public TriggerDescriptor getTrigger(String shortClassName) {
        return (TriggerDescriptor)findDescriptor(shortClassName, Triggers.TRIGGERS);
    }

    /**
     * Gets the {@link JobPropertyDescriptor} by name. Primarily used for making them web-visible.
     */
    public JobPropertyDescriptor getJobProperty(String shortClassName) {
        return (JobPropertyDescriptor)findDescriptor(shortClassName, Jobs.PROPERTIES);
    }

    /**
     * Finds a descriptor that has the specified name.
     */
    private <T extends Describable<T>>
    Descriptor<T> findDescriptor(String shortClassName, Collection<? extends Descriptor<T>> descriptors) {
        String name = '.'+shortClassName;
        for (Descriptor<T> d : descriptors) {
            if(d.clazz.getName().endsWith(name))
                return d;
        }
        return null;
    }

    /**
     * Adds a new {@link JobListener}.
     *
     * @deprecated
     *      Use {@code getJobListners().add(l)} instead.
     */
    public void addListener(JobListener l) {
        viewItemListeners.add(new JobListenerAdapter(l));
    }

    /**
     * Deletes an existing {@link JobListener}.
     *
     * @deprecated
     *      Use {@code getJobListners().remove(l)} instead.
     */
    public boolean removeListener(JobListener l ) {
        return viewItemListeners.remove(new JobListenerAdapter(l));
    }

    /**
     * Gets all the installed {@link ItemListener}s.
     */
    public CopyOnWriteList<ItemListener> getJobListeners() {
        return viewItemListeners;
    }

    /**
     * Gets all the installed {@link SCMListener}s.
     */
    public CopyOnWriteList<SCMListener> getSCMListeners() {
        return scmListeners;
    }

    /**
     * Gets the plugin object from its short name.
     *
     * <p>
     * This allows URL <tt>hudson/plugin/ID</tt> to be served by the views
     * of the plugin class.
     */
    public Plugin getPlugin(String shortName) {
        PluginWrapper p = pluginManager.getPlugin(shortName);
        if(p==null)     return null;
        return p.getPlugin();
    }

    /**
     * Synonym to {@link #getNodeDescription()}.
     */
    public String getSystemMessage() {
        return systemMessage;
    }

    public Launcher createLauncher(TaskListener listener) {
        return new LocalLauncher(listener);
    }

    /**
     * Updates {@link #computers} by using {@link #getSlaves()}.
     *
     * <p>
     * This method tries to reuse existing {@link Computer} objects
     * so that we won't upset {@link Executor}s running in it.
     */
    private void updateComputerList() throws IOException {
        synchronized(computers) {// this synchronization is still necessary so that no two update happens concurrently
            Map<String,Computer> byName = new HashMap<String,Computer>();
            for (Computer c : computers.values()) {
                if(c.getNode()==null)
                    continue;   // this computer is gone
                byName.put(c.getNode().getNodeName(),c);
            }

            Set<Computer> old = new HashSet<Computer>(computers.values());
            Set<Computer> used = new HashSet<Computer>();

            updateComputer(this, byName, used);
            for (Slave s : getSlaves())
                updateComputer(s, byName, used);

            // find out what computers are removed, and kill off all executors.
            // when all executors exit, it will be removed from the computers map.
            // so don't remove too quickly
            old.removeAll(used);
            for (Computer c : old) {
                c.kill();
            }
        }
        getQueue().scheduleMaintenance();
    }

    private void updateComputer(Node n, Map<String,Computer> byNameMap, Set<Computer> used) {
        Computer c;
        c = byNameMap.get(n.getNodeName());
        if (c!=null) {
            c.setNode(n); // reuse
        } else {
            if(n.getNumExecutors()>0)
                computers.put(n,c=n.createComputer());
        }
        used.add(c);
    }

    /*package*/ void removeComputer(Computer computer) {
        for (Entry<Node, Computer> e : computers.entrySet()) {
            if (e.getValue() == computer) {
                computers.remove(e.getKey());
                return;
            }
        }
        throw new IllegalStateException("Trying to remove unknown computer");
    }

    public String getFullName() {
        return "";
    }

    public String getFullDisplayName() {
        return "";
    }

    /**
     * Gets just the immediate children of {@link Hudson}.
     *
     * @see #getAllItems(Class)
     */
    public List<TopLevelItem> getItems() {
        return new ArrayList<TopLevelItem>(items.values());
    }

    /**
     * Gets all the {@link Item}s recursively in the {@link ItemGroup} tree
     * and filter them by the given type.
     */
    public <T extends Item> List<T> getAllItems(Class<T> type) {
        List<T> r = new ArrayList<T>();

        Stack<ItemGroup> q = new Stack<ItemGroup>();
        q.push(this);

        while(!q.isEmpty()) {
            ItemGroup<?> parent = q.pop();
            for (Item i : parent.getItems()) {
                if(type.isInstance(i))
                    r.add(type.cast(i));
                if(i instanceof ItemGroup)
                    q.push((ItemGroup)i);
            }
        }

        return r;
    }

    /**
     * Gets the list of all the projects.
     *
     * <p>
     * Since {@link Project} can only show up under {@link Hudson},
     * no need to search recursively.
     */
    public List<Project> getProjects() {
        return Util.createSubList(items.values(),Project.class);
    }

    /**
     * Gets the names of all the {@link Job}s.
     */
    public Collection<String> getJobNames() {
        List<String> names = new ArrayList<String>();
        for (Job j : getAllItems(Job.class))
            names.add(j.getName());
        return names;
    }

    /**
     * Gets the names of all the {@link TopLevelItem}s.
     */
    public Collection<String> getTopLevelItemNames() {
        List<String> names = new ArrayList<String>();
        for (TopLevelItem j : items.values())
            names.add(j.getName());
        return names;
    }

    /**
     * Every job belongs to us.
     *
     * @deprecated
     *      why are you calling a method that always return true?
     */
    @Deprecated
    public boolean contains(TopLevelItem view) {
        return true;
    }

    public synchronized View getView(String name) {
        if(views!=null) {
            for (ListView v : views) {
                if(v.getViewName().equals(name))
                    return v;
            }
        }
        if(this.getViewName().equals(name))
            return this;
        else
            return null;
    }

    /**
     * Gets the read-only list of all {@link View}s.
     */
    @Exported
    public synchronized View[] getViews() {
        if(views==null)
            views = new ArrayList<ListView>();
        View[] r = new View[views.size()+1];
        views.toArray(r);
        // sort Views and put "all" at the very beginning
        r[r.length-1] = r[0];
        Arrays.sort(r,1,r.length, View.SORTER);
        r[0] = this;
        return r;
    }

    public synchronized void deleteView(ListView view) throws IOException {
        if(views!=null) {
            views.remove(view);
            save();
        }
    }

    public String getViewName() {
        return "All";
    }

    /**
     * Gets the read-only list of all {@link Computer}s.
     */
    public Computer[] getComputers() {
        Computer[] r = computers.values().toArray(new Computer[computers.size()]);
        Arrays.sort(r,new Comparator<Computer>() {
            public int compare(Computer lhs, Computer rhs) {
                if(lhs.getNode()==Hudson.this)  return -1;
                if(rhs.getNode()==Hudson.this)  return 1;
                return lhs.getDisplayName().compareTo(rhs.getDisplayName());
            }
        });
        return r;
    }

    public Computer getComputer(String name) {
        if(name.equals("(master)"))
            name = "";

        for (Computer c : computers.values()) {
            if(c.getNode().getNodeName().equals(name))
                return c;
        }
        return null;
    }

    /**
     * @deprecated
     *      UI method. Not meant to be used programatically.
     */
    public ComputerSet getComputer() {
        return new ComputerSet();
    }

    /**
     * Gets the label that exists on this system by the name.
     *
     * @return null if no such label exists.
     */
    public Label getLabel(String name) {
        if(name==null)  return null;
        while(true) {
            Label l = labels.get(name);
            if(l!=null)
                return l;

            // non-existent
            labels.putIfAbsent(name,new Label(name));
        }
    }

    /**
     * Gets all the active labels in the current system.
     */
    public Set<Label> getLabels() {
        Set<Label> r = new TreeSet<Label>();
        for (Label l : labels.values()) {
            if(!l.getNodes().isEmpty())
                r.add(l);
        }
        return r;
    }

    public Queue getQueue() {
        return queue;
    }

    public String getDisplayName() {
        return "Hudson";
    }

    public List<JDK> getJDKs() {
        if(jdks==null)
            jdks = new ArrayList<JDK>();
        return jdks;
    }

    /**
     * Gets the JDK installation of the given name, or returns null.
     */
    public JDK getJDK(String name) {
        if(name==null)  return null;
        for (JDK j : getJDKs()) {
            if(j.getName().equals(name))
                return j;
        }
        return null;
    }

    /**
     * Gets the slave node of the give name, hooked under this Hudson.
     */
    public Slave getSlave(String name) {
        for (Slave s : getSlaves()) {
            if(s.getNodeName().equals(name))
                return s;
        }
        return null;
    }

    public List<Slave> getSlaves() {
        return Collections.unmodifiableList(slaves);
    }

    /**
     * Gets the system default quiet period.
     */
    public int getQuietPeriod() {
        return quietPeriod!=null ? quietPeriod : 5;
    }

    /**
     * @deprecated
     *      Why are you calling a method that always returns ""?
     *      Perhaps you meant {@link #getRootUrl()}.
     */
    public String getUrl() {
        return "";
    }

    @Override
    public SearchIndexBuilder makeSearchIndex() {
        return super.makeSearchIndex()
            .add("configure", "config","configure")
            .add("manage")
            .add("log")
            .add(new CollectionSearchIndex() {// for computers
                protected Computer get(String key) { return getComputer(key); }
                protected Collection<Computer> all() { return computers.values(); }
            })
            .add(new CollectionSearchIndex() {// for users
                protected User get(String key) { return User.get(key,false); }
                protected Collection<User> all() { return User.getAll(); }
            })
            .add(new CollectionSearchIndex() {// for views
                protected View get(String key) { return getView(key); }
                protected Collection<ListView> all() { return views; }
            });
    }

    public String getUrlChildPrefix() {
        return "job";
    }

    /**
     * Gets the absolute URL of Hudson,
     * such as "http://localhost/hudson/".
     *
     * <p>
     * Also note that when serving user requests from HTTP, you should always use
     * {@link HttpServletRequest} to determine the full URL, instead of using this
     * (this is because one host may have multiple names, and {@link HttpServletRequest}
     * accurately represents what the current user used.)
     *
     * <p>
     * This information is rather only meant to be useful for sending out messages
     * via non-HTTP channels, like SMTP or IRC, with a link back to Hudson website.
     *
     * @return
     *      This method returns null if this parameter is not configured by the user.
     *      The caller must gracefully deal with this situation.
     *      The returned URL will always have the trailing '/'.
     * @since 1.66
     */
    public String getRootUrl() {
        // for compatibility. the actual data is stored in Mailer
        return Mailer.DESCRIPTOR.getUrl();
    }

    public File getRootDir() {
        return root;
    }

    public FilePath getWorkspaceFor(TopLevelItem item) {
        return new FilePath(new File(item.getRootDir(),"workspace"));
    }

    public FilePath getRootPath() {
        return new FilePath(getRootDir());
    }

    public ClockDifference getClockDifference() {
        return ClockDifference.ZERO;
    }

    public boolean isUseSecurity() {
        return useSecurity;
    }

    public void setUseSecurity(boolean useSecurity) {
        this.useSecurity = useSecurity;
    }

    /**
     * Returns true if Hudson is quieting down.
     * <p>
     * No further jobs will be executed unless it
     * can be finished while other current pending builds
     * are still in progress.
     */
    public boolean isQuietingDown() {
        return isQuietingDown;
    }

    /**
     * Returns true if the container initiated the termination of the web application.
     */
    public boolean isTerminating() {
        return terminating;
    }

    /**
     * @deprecated
     *      Left only for the compatibility of URLs.
     *      Should not be invoked for any other purpose.
     */
    public TopLevelItem getJob(String name) {
        return getItem(name);
    }

    /**
     * @deprecated
     *      Used only for mapping jobs to URL in a case-insensitive fashion.
     */
    public TopLevelItem getJobCaseInsensitive(String name) {
        for (Entry<String, TopLevelItem> e : items.entrySet()) {
            if(e.getKey().equalsIgnoreCase(name))
                return e.getValue();
        }
        return null;
    }

    @Override
    public TopLevelItem getItem(String name) {
        return items.get(name);
    }

    public File getRootDirFor(TopLevelItem child) {
        return getRootDirFor(child.getName());
    }

    private File getRootDirFor(String name) {
        return new File(new File(getRootDir(),"jobs"), name);
    }

    /**
     * Gets the {@link Item} object by its full name.
     * Full names are like path names, where each name of {@link Item} is
     * combined by '/'.
     *
     * @return
     *      null if either such {@link Item} doesn't exist under the given full name,
     *      or it exists but it's no an instance of the given type.
     */
    public <T extends Item> T getItemByFullName(String fullName, Class<T> type) {
        StringTokenizer tokens = new StringTokenizer(fullName,"/");
        ItemGroup parent = this;

        while(true) {
            Item item = parent.getItem(tokens.nextToken());
            if(!tokens.hasMoreTokens()) {
                if(type.isInstance(item))
                    return type.cast(item);
                else
                    return null;
            }

            if(!(item instanceof ItemGroup))
                return null;    // this item can't have any children

            parent = (ItemGroup) item;
        }
    }

    public Item getItemByFullName(String fullName) {
        return getItemByFullName(fullName,Item.class);
    }

    /**
     * Gets the user of the given name.
     *
     * @return
     *      This method returns a non-null object for any user name, without validation.
     */
    public User getUser(String name) {
        return User.get(name);
    }

    /**
     * Creates a new job.
     *
     * @throws IllegalArgumentException
     *      if the project of the given name already exists.
     */
    public synchronized TopLevelItem createProject( TopLevelItemDescriptor type, String name ) throws IOException {
        if(items.containsKey(name))
            throw new IllegalArgumentException();

        TopLevelItem item;
        try {
            item = type.newInstance(name);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }

        item.save();
        items.put(name,item);
        return item;
    }

    /**
     * Called in response to {@link Job#doDoDelete(StaplerRequest, StaplerResponse)}
     */
    /*package*/ void deleteJob(TopLevelItem item) throws IOException {
        for (ItemListener l : viewItemListeners)
            l.onDeleted(item);

        items.remove(item.getName());
        if(views!=null) {
            for (ListView v : views) {
                synchronized(v) {
                    v.jobNames.remove(item.getName());
                }
            }
            save();
        }
    }

    /**
     * Called by {@link Job#renameTo(String)} to update relevant data structure.
     * assumed to be synchronized on Hudson by the caller.
     */
    /*package*/ void onRenamed(TopLevelItem job, String oldName, String newName) throws IOException {
        items.remove(oldName);
        items.put(newName,job);

        if(views!=null) {
            for (ListView v : views) {
                synchronized(v) {
                    if(v.jobNames.remove(oldName))
                        v.jobNames.add(newName);
                }
            }
            save();
        }
    }

    public FingerprintMap getFingerprintMap() {
        return fingerprintMap;
    }

    // if no finger print matches, display "not found page".
    public Object getFingerprint( String md5sum ) throws IOException {
        Fingerprint r = fingerprintMap.get(md5sum);
        if(r==null)     return new NoFingerprintMatch(md5sum);
        else            return r;
    }

    /**
     * Gets a {@link Fingerprint} object if it exists.
     * Otherwise null.
     */
    public Fingerprint _getFingerprint( String md5sum ) throws IOException {
        return fingerprintMap.get(md5sum);
    }

    /**
     * The file we save our configuration.
     */
    private XmlFile getConfigFile() {
        return new XmlFile(XSTREAM, new File(root,"config.xml"));
    }

    public int getNumExecutors() {
        return numExecutors;
    }

    public Mode getMode() {
        return Mode.NORMAL;
    }

    public Set<Label> getAssignedLabels() {
        if (labelSet == null) {
            Set<Label> r = new HashSet<Label>();
            r.addAll(getDynamicLabels());
            r.add(getSelfLabel());
            this.labelSet = Collections.unmodifiableSet(r);
        }
        return labelSet;
    }

    /**
     * Returns the possibly empty set of labels that it has been determined as supported by this node.
     *
     * @see hudson.tasks.LabelFinder
     */
    public Set<Label> getDynamicLabels() {
        if (dynamicLabels == null) {
            synchronized (this) {
                Computer comp = getComputer("");
                if (dynamicLabels == null) {
                    dynamicLabels = new HashSet<Label>();
                    if (comp != null) {
                        VirtualChannel channel = comp.getChannel();
                        if (channel != null) {
                            for (DynamicLabeler labeler : LabelFinder.LABELERS) {
                                for (String label : labeler.findLabels(channel)) {
                                    dynamicLabels.add(getLabel(label));
                                }
                            }
                        }
                    }
                }
            }
        }
        return dynamicLabels;
    }

    public Label getSelfLabel() {
        return getLabel("master");
    }

    public Computer createComputer() {
        return new MasterComputer();
    }

    private synchronized void load() throws IOException {
        long startTime = System.currentTimeMillis();
        XmlFile cfg = getConfigFile();
        if(cfg.exists())
            cfg.unmarshal(this);

        File projectsDir = new File(root,"jobs");
        if(!projectsDir.isDirectory() && !projectsDir.mkdirs()) {
            if(projectsDir.exists())
                throw new IOException(projectsDir+" is not a directory");
            throw new IOException("Unable to create "+projectsDir+"\nPermission issue? Please create this directory manually.");
        }
        File[] subdirs = projectsDir.listFiles(new FileFilter() {
            public boolean accept(File child) {
                return child.isDirectory();
            }
        });
        items.clear();
        if(parallelLoad) {
            // load jobs in parallel for better performance
            List<Future<TopLevelItem>> loaders = new ArrayList<Future<TopLevelItem>>();
            for (final File subdir : subdirs) {
                loaders.add(threadPoolForLoad.submit(new Callable<TopLevelItem>() {
                    public TopLevelItem call() throws Exception {
                        return (TopLevelItem) Items.load(Hudson.this, subdir);
                    }
                }));
            }

            for (Future<TopLevelItem> loader : loaders) {
                try {
                    TopLevelItem item = loader.get();
                    items.put(item.getName(), item);
                } catch (ExecutionException e) {
                    LOGGER.log(Level.WARNING, "Failed to loa da project",e.getCause());
                } catch (InterruptedException e) {
                    e.printStackTrace(); // this is probably not the right thing to do
                }
            }
        } else {
            for (File subdir : subdirs) {
                try {
                    TopLevelItem item = (TopLevelItem)Items.load(this,subdir);
                    items.put(item.getName(), item);
                } catch (Error e) {
                    LOGGER.log(Level.WARNING, "Failed to load "+subdir,e);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to load "+subdir,e);
                }
            }
        }
        rebuildDependencyGraph();

        // recompute label objects
        if (null != slaves) { // only if we have slaves
            for (Slave slave : slaves)
                slave.getAssignedLabels();
        }

        LOGGER.info(String.format("Took %s ms to load",System.currentTimeMillis()-startTime));
    }

    /**
     * Save the settings to a file.
     */
    public synchronized void save() throws IOException {
        getConfigFile().write(this);
    }


    /**
     * Called to shut down the system.
     */
    public void cleanUp() {
        terminating = true;
        for( Computer c : computers.values() ) {
            c.interrupt();
            c.kill();
        }
        ExternalJob.reloadThread.interrupt();
        Trigger.timer.cancel();
        if(tcpSlaveAgentListener!=null)
            tcpSlaveAgentListener.shutdown();

        if(pluginManager!=null) // be defensive. there could be some ugly timing related issues
            pluginManager.stop();

        if(getRootDir().exists())
            // if we are aborting because we failed to create HUDSON_HOME,
            // don't try to save. Issue #536
            getQueue().save();

        threadPoolForLoad.shutdown();
    }



//
//
// actions
//
//
    /**
     * Accepts submission from the configuration page.
     */
    public synchronized void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        try {
            if(!Hudson.adminCheck(req,rsp))
                return;

            req.setCharacterEncoding("UTF-8");

            useSecurity = req.getParameter("use_security")!=null;

            {
                String v = req.getParameter("slaveAgentPortType");
                if(!useSecurity || v==null || v.equals("random"))
                    slaveAgentPort = 0;
                else
                if(v.equals("disable"))
                    slaveAgentPort = -1;
                else {
                    try {
                        slaveAgentPort = Integer.parseInt(req.getParameter("slaveAgentPort"));
                    } catch (NumberFormatException e) {
                        throw new FormException("Bad port number "+req.getParameter("slaveAgentPort"),"slaveAgentPort");
                    }
                }

                // relaunch the agent
                if(tcpSlaveAgentListener==null) {
                    if(slaveAgentPort!=-1)
                        tcpSlaveAgentListener = new TcpSlaveAgentListener(slaveAgentPort);
                } else {
                    if(tcpSlaveAgentListener.configuredPort!=slaveAgentPort) {
                        tcpSlaveAgentListener.shutdown();
                        tcpSlaveAgentListener = null;
                        if(slaveAgentPort!=-1)
                            tcpSlaveAgentListener = new TcpSlaveAgentListener(slaveAgentPort);
                    }
                }
            }

            numExecutors = Integer.parseInt(req.getParameter("numExecutors"));
            quietPeriod = Integer.parseInt(req.getParameter("quiet_period"));

            systemMessage = Util.nullify(req.getParameter("system_message"));

            {// update slave list
                List<Slave> newSlaves = new ArrayList<Slave>();
                String[] names = req.getParameterValues("slave.name");
                if(names!=null) {
                    for(int i=0;i< names.length;i++) {
                        newSlaves.add(req.bindParameters(Slave.class,"slave.",i));
                    }
                }
                this.slaves = newSlaves;
                updateComputerList();

                // label trim off
                for (Label l : labels.values()) {
                    l.reset();
                    if(l.getNodes().isEmpty())
                        labels.remove(l);
                }
            }

            {// update JDK installations
                jdks.clear();
                String[] names = req.getParameterValues("jdk_name");
                String[] homes = req.getParameterValues("jdk_home");
                if(names!=null && homes!=null) {
                    int len = Math.min(names.length,homes.length);
                    for(int i=0;i<len;i++) {
                        jdks.add(new JDK(names[i],homes[i]));
                    }
                }
            }

            boolean result = true;

            for( Descriptor<Builder> d : BuildStep.BUILDERS )
                result &= d.configure(req);

            for( Descriptor<Publisher> d : BuildStep.PUBLISHERS )
                result &= d.configure(req);

            for( Descriptor<BuildWrapper> d : BuildWrappers.WRAPPERS )
                result &= d.configure(req);

            for( SCMDescriptor scmd : SCMS.SCMS )
                result &= scmd.configure(req);

            for( TriggerDescriptor d : Triggers.TRIGGERS )
                result &= d.configure(req);

            for( JobPropertyDescriptor d : Jobs.PROPERTIES )
                result &= d.configure(req);

            save();
            if(result)
                rsp.sendRedirect(req.getContextPath()+'/');  // go to the top page
            else
                rsp.sendRedirect("configure"); // back to config
        } catch (FormException e) {
            sendError(e,req,rsp);
        }
    }

    /**
     * Accepts the new description.
     */
    public synchronized void doSubmitDescription( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        req.setCharacterEncoding("UTF-8");
        systemMessage = req.getParameter("description");
        save();
        rsp.sendRedirect(".");
    }

    public synchronized void doQuietDown( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;
        isQuietingDown = true;
        rsp.sendRedirect2(".");
    }

    public synchronized void doCancelQuietDown( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;
        isQuietingDown = false;
        getQueue().scheduleMaintenance();
        rsp.sendRedirect2(".");
    }

    /**
     * Backward compatibility. Redirect to the thread dump.
     */
    public void doClassicThreadDump( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rsp.sendRedirect2("threadDump");
    }

    public synchronized Item doCreateItem( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return null;

        TopLevelItem result;

        boolean isXmlSubmission = req.getContentType().startsWith("application/xml") || req.getContentType().startsWith("text/xml");
        if(!isXmlSubmission) {
            // containers often implement RFCs incorrectly in that it doesn't interpret query parameter
            // decoding with UTF-8. This will ensure we get it right.
            // but doing this for config.xml submission could potentiall overwrite valid
            // "text/xml;charset=xxx"
            req.setCharacterEncoding("UTF-8");
        }
        
        String name = req.getParameter("name").trim();
        String mode = req.getParameter("mode");

        try {
            checkGoodName(name);
        } catch (ParseException e) {
            rsp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendError(e,req,rsp);
            return null;
        }

        if(getItem(name)!=null) {
            rsp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            sendError("A job already exists with the name '"+name+"'",req,rsp);
            return null;
        }

        if(mode!=null && mode.equals("copyJob")) {
            TopLevelItem src = getItem(req.getParameter("from"));
            if(src==null) {
                rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return null;
            }

            result = createProject(src.getDescriptor(),name);

            // copy config
            Util.copyFile(Items.getConfigFile(src).getFile(),Items.getConfigFile(result).getFile());

            // reload from the new config
            result = (TopLevelItem)Items.load(this,result.getRootDir());
            result.onCopiedFrom(src);
            items.put(name,result);
        } else {
            if(isXmlSubmission) {
                // config.xml submission

                // first copy it as config.xml
                File configXml = Items.getConfigFile(getRootDirFor(name)).getFile();
                configXml.getParentFile().mkdirs();
                try {
                    FileOutputStream fos = new FileOutputStream(configXml);
                    try {
                        Util.copyStream(req.getInputStream(),fos);
                    } finally {
                        fos.close();
                    }

                    // load it
                    result = (TopLevelItem)Items.load(this,configXml.getParentFile());
                    items.put(name,result);
                } catch (IOException e) {
                    // if anything fails, delete the config file to avoid further confusion
                    Util.deleteRecursive(configXml.getParentFile());
                    throw e;
                }
            } else {
                // create empty job and redirect to the project config screen
                if(mode==null) {
                    rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return null;
                }
                result = createProject(Items.getDescriptor(mode), name);
            }
        }

        for (ItemListener l : viewItemListeners)
            l.onCreated(result);

        if(isXmlSubmission) {
            // it worked
            rsp.setStatus(HttpServletResponse.SC_OK);
        } else {
            // send the browser to the config page
            rsp.sendRedirect2(req.getContextPath()+'/'+result.getUrl()+"configure");
        }

        return result;
    }

    public synchronized void doCreateView( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        req.setCharacterEncoding("UTF-8");

        String name = req.getParameter("name");

        try {
            checkGoodName(name);
        } catch (ParseException e) {
            sendError(e, req, rsp);
            return;
        }

        ListView v = new ListView(this, name);
        if(views==null)
            views = new Vector<ListView>();
        views.add(v);
        save();

        // redirect to the config screen
        rsp.sendRedirect2("./"+v.getUrl()+"configure");
    }

    /**
     * Check if the given name is suitable as a name
     * for job, view, etc.
     *
     * @throws ParseException
     *      if the given name is not good
     */
    public static void checkGoodName(String name) throws ParseException {
        if(name==null || name.length()==0)
            throw new ParseException("No name is specified",0);

        for( int i=0; i<name.length(); i++ ) {
            char ch = name.charAt(i);
            if(Character.isISOControl(ch))
                throw new ParseException("No control code is allowed",i);
            if("?*()/\\%!@#$^&|<>[]".indexOf(ch)!=-1)
                throw new ParseException("'"+ch+"' is an unsafe character",i);
        }

        // looks good
    }

    /**
     * Checks if the user was successfully authenticated.
     *
     * @see BasicAuthenticationFilter
     */
    public void doSecured( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(req.getUserPrincipal()==null) {
            // authentication must have failed
            rsp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // the user is now authenticated, so send him back to the target
        String path = req.getContextPath()+req.getRestOfPath();
        String q = req.getQueryString();
        if(q!=null)
            path += '?'+q;

        rsp.sendRedirect2(path);
    }

    /**
     * Called once the user logs in. Just forward to the top page.
     */
    public void doLoginEntry( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        if(req.getUserPrincipal()==null)
            rsp.sendRedirect2("noPrincipal");

        String from = req.getParameter("from");
        if(from!=null && from.startsWith("/") && !from.equals("/loginError"))
            rsp.sendRedirect2(from);    // I'm bit uncomfortable letting users redircted to other sites, make sure the URL falls into this domain
        else
            rsp.sendRedirect2(".");
    }

    /**
     * Called once the user logs in. Just forward to the top page.
     */
    public void doLogout( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        HttpSession session = req.getSession(false);
        if(session!=null)
            session.invalidate();
        rsp.sendRedirect2(req.getContextPath()+"/");
    }

    /**
     * RSS feed for log entries.
     */
    public void doLogRss( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        List<LogRecord> logs = logRecords;

        // filter log records based on the log level
        String level = req.getParameter("level");
        if(level!=null) {
            Level threshold = Level.parse(level);
            List<LogRecord> filtered = new ArrayList<LogRecord>();
            for (LogRecord r : logs) {
                if(r.getLevel().intValue() >= threshold.intValue())
                    filtered.add(r);
            }
            logs = filtered;
        }

        RSS.forwardToRss("Hudson log","", logs, new FeedAdapter<LogRecord>() {
            public String getEntryTitle(LogRecord entry) {
                return entry.getMessage();
            }

            public String getEntryUrl(LogRecord entry) {
                return "log";   // TODO: one URL for one log entry?
            }

            public String getEntryID(LogRecord entry) {
                return String.valueOf(entry.getSequenceNumber());
            }

            public String getEntryDescription(LogRecord entry) {
                return Functions.printLogRecord(entry);
            }

            public Calendar getEntryTimestamp(LogRecord entry) {
                GregorianCalendar cal = new GregorianCalendar();
                cal.setTimeInMillis(entry.getMillis());
                return cal;
            }
        },req,rsp);
    }

    /**
     * Reloads the configuration.
     */
    public synchronized void doReload( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        // engage "loading ..." UI and then run the actual task in a separate thread
        final ServletContext context = req.getServletContext();
        context.setAttribute("app",new HudsonIsLoading());

        rsp.sendRedirect2(req.getContextPath()+"/");

        new Thread("Hudson config reload thread") {
            public void run() {
                try {
                    load();
                    User.reload();
                    context.setAttribute("app",Hudson.this);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE,"Failed to reload Hudson config",e);
                }
            }
        }.start();
    }

    public boolean isPluginUploaded() {
        return pluginUploaded;
    }

    /**
     * Uploads a plugin.
     */
    public void doUploadPlugin( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        try {
            if(!Hudson.adminCheck(req,rsp))
                return;

            ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());

            // Parse the request
            FileItem fileItem = (FileItem) upload.parseRequest(req).get(0);
            String fileName = Util.getFileName(fileItem.getName());
            if(!fileName.endsWith(".hpi")) {
                sendError(fileName+" is not a Hudson plugin",req,rsp);
                return;
            }
            fileItem.write(new File(getPluginManager().rootDir, fileName));
            fileItem.delete();

            pluginUploaded=true;

            rsp.sendRedirect2("managePlugins");
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {// grrr. fileItem.write throws this
            throw new ServletException(e);
        }
    }

    /**
     * Do a finger-print check.
     */
    public void doDoFingerprintCheck( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        // Parse the request
        MultipartFormDataParser p = new MultipartFormDataParser(req);
        try {
            rsp.sendRedirect2(req.getContextPath()+"/fingerprint/"+
                Util.getDigestOf(p.getFileItem("name").getInputStream())+'/');
        } finally {
            p.cleanUp();
        }
    }

    /**
     * Serves static resources without the "Last-Modified" header to work around
     * a bug in Firefox.
     *
     * <p>
     * See https://bugzilla.mozilla.org/show_bug.cgi?id=89419
     */
    public void doNocacheImages( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        String path = req.getRestOfPath();

        if(path.length()==0)
            path = "/";

        if(path.indexOf("..")!=-1 || path.length()<1) {
            // don't serve anything other than files in the artifacts dir
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        File f = new File(req.getServletContext().getRealPath("/images"),path.substring(1));
        if(!f.exists()) {
            rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if(f.isDirectory()) {
            // listing not allowed
            rsp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        FileInputStream in = new FileInputStream(f);
        // serve the file
        String contentType = req.getServletContext().getMimeType(f.getPath());
        rsp.setContentType(contentType);
        rsp.setContentLength((int)f.length());
        byte[] buf = new byte[1024];
        int len;
        while((len=in.read(buf))>0)
            rsp.getOutputStream().write(buf,0,len);
        in.close();
    }

    /**
     * For debugging. Expose URL to perform GC.
     */
    public void doGc( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        System.gc();
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/plain");
        rsp.getWriter().println("GCed");
    }

    /**
     * For system diagnostics.
     * Run arbitrary Groovy script.
     */
    public void doScript( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!adminCheck(req,rsp))
            return; // ability to run arbitrary script is dangerous

        String text = req.getParameter("script");
        if(text!=null) {
            GroovyShell shell = new GroovyShell();

            StringWriter out = new StringWriter();
            PrintWriter pw = new PrintWriter(out);
            shell.setVariable("out", pw);
            try {
                Object output = shell.evaluate(text);
                if(output!=null)
                pw.println("Result: "+output);
            } catch (Throwable t) {
                t.printStackTrace(pw);
            }
            req.setAttribute("output",out);
        }

        req.getView(this,"_script.jelly").forward(req,rsp);
    }

    /**
     * Changes the icon size by changing the cookie
     */
    public void doIconSize( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        String qs = req.getQueryString();
        if(!ICON_SIZE.matcher(qs).matches())
            throw new ServletException();
        Cookie cookie = new Cookie("iconSize", qs);
        cookie.setMaxAge(/* ~4 mo. */9999999); // #762
        rsp.addCookie(cookie);
        String ref = req.getHeader("Referer");
        if(ref==null)   ref=".";
        rsp.sendRedirect2(ref);
    }

    public void doFingerprintCleanup( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        FingerprintCleanupThread.invoke();
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/plain");
        rsp.getWriter().println("Invoked");
    }

    public void doWorkspaceCleanup( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        WorkspaceCleanupThread.invoke();
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/plain");
        rsp.getWriter().println("Invoked");
    }

    /**
     * Checks if the path is a valid path.
     */
    public void doCheckLocalFSRoot( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        // this can be used to check the existence of a file on the server, so needs to be protected
        new FormFieldValidator(req,rsp,true) {
            public void check() throws IOException, ServletException {
                File f = getFileParameter("value");
                if(f.isDirectory()) {// OK
                    ok();
                } else {// nope
                    if(f.exists()) {
                        error(f+" is not a directory");
                    } else {
                        error("No such directory: "+f);
                    }
                }
            }
        }.process();
    }

    /**
     * Check the clock difference between slave and this node.
     */
    public void doCheckClock( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        new FormFieldValidator(req,rsp,false) {
            public void check() throws IOException, ServletException {
                response.setContentType("text/html");
                response.getWriter().print(
                    ClockDifference.toHtml(getSlave(request.getParameter("node"))));
            }
        }.process();
    }

    /**
     * Checks if the JAVA_HOME is a valid JAVA_HOME path.
     */
    public void doJavaHomeCheck( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        // this can be used to check the existence of a file on the server, so needs to be protected
        new FormFieldValidator(req,rsp,true) {
            public void check() throws IOException, ServletException {
                File f = getFileParameter("value");
                if(!f.isDirectory()) {
                    error(f+" is not a directory");
                    return;
                }

                File toolsJar = new File(f,"lib/tools.jar");
                File mac = new File(f,"lib/dt.jar");
                if(!toolsJar.exists() && !mac.exists()) {
                    error(f+" doesn't look like a JDK directory");
                    return;
                }

                ok();
            }
        }.process();
    }

    /**
     * If the user chose the default JDK, make sure we got 'java' in PATH.
     */
    public void doDefaultJDKCheck( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        new FormFieldValidator(req,rsp,true) {
            public void check() throws IOException, ServletException {
                String v = request.getParameter("value");
                if(!v.equals("(Default)"))
                    // assume the user configured named ones properly in system config ---
                    // or else system config should have reported form field validation errors.
                    ok();
                else {
                    // default JDK selected. Does such java really exist?
                    if(JDK.isDefaultJDKValid(Hudson.this))
                        ok();
                    else
                        errorWithMarkup(
                            "java is not in your PATH. Maybe you need to" +
                            "<a href='"+request.getContextPath()+"/configure'>configure JDKs</a>?");
                }
            }
        }.process();
    }

    /**
     * Checks if the top-level item with the given name exists.
     */
    public void doItemExistsCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        // this method can be used to check if a file exists anywhere in the file system,
        // so it should be protected.
        new FormFieldValidator(req,rsp,true) {
            protected void check() throws IOException, ServletException {
                String job = fixEmpty(request.getParameter("value"));
                if(job==null) {
                    ok(); // nothing is entered yet
                    return;
                }

                if(getItem(job)==null)
                    ok();
                else
                    error("Job named "+job+" already exists");
            }
        }.process();
    }


    public static boolean isWindows() {
        return File.pathSeparatorChar==';';
    }


    /**
     * Returns all {@code CVSROOT} strings used in the current Hudson installation.
     *
     * <p>
     * Ideally this shouldn't be defined in here
     * but EL doesn't provide a convenient way of invoking a static function,
     * so I'm putting it here for now.
     */
    public Set<String> getAllCvsRoots() {
        Set<String> r = new TreeSet<String>();
        for( AbstractProject p : getAllItems(AbstractProject.class) ) {
            SCM scm = p.getScm();
            if (scm instanceof CVSSCM) {
                CVSSCM cvsscm = (CVSSCM) scm;
                r.add(cvsscm.getCvsRoot());
            }
        }

        return r;
    }

    /**
     * Rebuilds the dependency map.
     */
    public void rebuildDependencyGraph() {
        dependencyGraph = new DependencyGraph();
    }

    public DependencyGraph getDependencyGraph() {
        return dependencyGraph;
    }

    public static final class MasterComputer extends Computer {
        private MasterComputer() {
            super(Hudson.getInstance());
        }

        @Override
        public String getDisplayName() {
            return "master";
        }

        @Override
        public String getCaption() {
            return "Master";
        }

        public String getUrl() {
            return "computer/(master)/";
        }

        @Override
        public VirtualChannel getChannel() {
            return localChannel;
        }

        public List<LogRecord> getLogRecords() throws IOException, InterruptedException {
            return logRecords;
        }

        public void doLaunchSlaveAgent(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            // this computer never returns null from channel, so
            // this method shall never be invoked.
            rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }

        public void launch() {
            // noop
        }

        /**
         * {@link LocalChannel} instance that can be used to execute programs locally.
         */
        public static final LocalChannel localChannel = new LocalChannel(threadPoolForRemoting);
    }

    public static boolean adminCheck() throws IOException {
        return adminCheck(Stapler.getCurrentRequest(), Stapler.getCurrentResponse());
    }

    public static boolean adminCheck(StaplerRequest req,StaplerResponse rsp) throws IOException {
        if (isAdmin(req)) return true;

        rsp.sendError(StaplerResponse.SC_FORBIDDEN);
        return false;
    }

    /**
     * Checks if the current user (for which we are processing the current request)
     * has the admin access.
     */
    public static boolean isAdmin() {
        return isAdmin(Stapler.getCurrentRequest());
    }

    public static boolean isAdmin(StaplerRequest req) {
        if(!getInstance().isUseSecurity())
            return true;

        if(req.isUserInRole("admin"))
            return true;
        return false;
    }

    /**
     * Live view of recent {@link LogRecord}s produced by Hudson.
     */
    public static List<LogRecord> logRecords = Collections.emptyList(); // initialized to dummy value to avoid NPE

    /**
     * Thread-safe reusable {@link XStream}.
     */
    private static final XStream XSTREAM = new XStream2();

    /**
     * Thread pool used to load configuration in parallel, to improve the start up time.
     * <p>
     * The idea here is to overlap the CPU and I/O, so we want more threads than CPU numbers.
     */
    /*package*/ static final ExecutorService threadPoolForLoad = new ThreadPoolExecutor(
        0, Runtime.getRuntime().availableProcessors() * 2,
        5L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new DaemonThreadFactory());



    /**
     * Version number of this Hudson.
     */
    public static String VERSION;

    /**
     * Prefix to static resources like images and javascripts in the war file.
     * Either "" or strings like "/static/VERSION", which avoids Hudson to pick up
     * stale cache when the user upgrades to a different version. 
     */
    public static String RESOURCE_PATH;

    public static boolean parallelLoad = Boolean.getBoolean(Hudson.class.getName()+".parallelLoad");

    private static final Logger LOGGER = Logger.getLogger(Hudson.class.getName());

    private static final Pattern ICON_SIZE = Pattern.compile("\\d+x\\d+");

    static {
        XSTREAM.alias("hudson",Hudson.class);
        XSTREAM.alias("slave",Slave.class);
        XSTREAM.alias("jdk",JDK.class);
        // for backward compatibility with <1.75, recognize the tag name "view" as well.
        XSTREAM.alias("view", ListView.class);
        XSTREAM.alias("listView", ListView.class);
    }
}
