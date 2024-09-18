/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly, Thomas J. Black
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

import static hudson.init.InitMilestone.JOB_CONFIG_ADAPTED;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.XmlFile;
import hudson.init.Initializer;
import hudson.model.Descriptor.FormException;
import hudson.model.listeners.SaveableListener;
import hudson.node_monitors.NodeMonitor;
import hudson.slaves.NodeDescriptor;
import hudson.triggers.SafeTimerTask;
import hudson.util.DescribableList;
import hudson.util.FormApply;
import hudson.util.FormValidation;
import jakarta.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.IComputer;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithChildren;
import jenkins.model.ModelObjectWithContextMenu.ContextMenu;
import jenkins.util.Timer;
import jenkins.widgets.HasWidgets;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;

/**
 * Serves as the top of {@link Computer}s in the URL hierarchy.
 * <p>
 * Getter methods are prefixed with '_' to avoid collision with computer names.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public final class ComputerSet extends AbstractModelObject implements Describable<ComputerSet>, ModelObjectWithChildren, HasWidgets {
    /**
     * This is the owner that persists {@link #monitors}.
     */
    private static final Saveable MONITORS_OWNER = new Saveable() {
        @Override
        public void save() throws IOException {
            getConfigFile().write(monitors);
            SaveableListener.fireOnChange(this, getConfigFile());
        }
    };

    private static final DescribableList<NodeMonitor, Descriptor<NodeMonitor>> monitors
            = new DescribableList<>(MONITORS_OWNER);

    @Override
    @Exported
    public String getDisplayName() {
        return Messages.ComputerSet_DisplayName();
    }

    /**
     * @deprecated as of 1.301
     *      Use {@link #getMonitors()}.
     */
    @Deprecated
    public static List<NodeMonitor> get_monitors() {
        return monitors.toList();
    }

    /**
     * @deprecated Use {@link #getComputers()} instead.
     * @return All {@link Computer} instances managed by this set.
     */
    @Deprecated(since = "TODO")
    public Computer[] get_all() {
        return getComputers().stream().filter(Computer.class::isInstance).toArray(Computer[]::new);
    }

    /**
     * @return All {@link IComputer} instances managed by this set.
     */
    @Exported(name = "computer", inline = true)
    public Collection<? extends IComputer> getComputers() {
        return ExtensionList.lookupFirst(ComputerSource.class).get();
    }

    /**
     * Allows plugins to override the displayed list of computers.
     */
    public interface ComputerSource extends ExtensionPoint {
        Collection<? extends IComputer> get();
    }

    @Override
    public ContextMenu doChildrenContextMenu(StaplerRequest2 request, StaplerResponse2 response) throws Exception {
        ContextMenu m = new ContextMenu();
        for (IComputer c : getComputers()) {
            m.add(c);
        }
        return m;
    }

    /**
     * Exposing {@link NodeMonitor#all()} for Jelly binding.
     */
    public DescriptorExtensionList<NodeMonitor, Descriptor<NodeMonitor>> getNodeMonitorDescriptors() {
        return NodeMonitor.all();
    }

    public static DescribableList<NodeMonitor, Descriptor<NodeMonitor>> getMonitors() {
        return monitors;
    }

    /**
     * Returns a subset pf {@link #getMonitors()} that are {@linkplain NodeMonitor#isIgnored() not ignored}.
     */
    public static Map<Descriptor<NodeMonitor>, NodeMonitor> getNonIgnoredMonitors() {
        Map<Descriptor<NodeMonitor>, NodeMonitor> r = new HashMap<>();
        for (NodeMonitor m : monitors) {
            if (!m.isIgnored())
                r.put(m.getDescriptor(), m);
        }
        return r;
    }

    /**
     * Gets all the agent names.
     */
    public List<String> get_slaveNames() {
        return new AbstractList<>() {
            final List<Node> nodes = Jenkins.get().getNodes();

            @Override
            public String get(int index) {
                return nodes.get(index).getNodeName();
            }

            @Override
            public int size() {
                return nodes.size();
            }
        };
    }

    /**
     * Number of total {@link Executor}s that belong to this label that are functioning.
     * <p>
     * This excludes executors that belong to offline nodes.
     */
    @Exported
    public int getTotalExecutors() {
        int r = 0;
        for (IComputer c : getComputers()) {
            if (c.isOnline())
                r += c.countExecutors();
        }
        return r;
    }

    /**
     * Number of busy {@link Executor}s that are carrying out some work right now.
     */
    @Exported
    public int getBusyExecutors() {
        int r = 0;
        for (IComputer c : getComputers()) {
            if (c.isOnline())
                r += c.countBusy();
        }
        return r;
    }

    /**
     * {@code getTotalExecutors()-getBusyExecutors()}, plus executors that are being brought online.
     */
    public int getIdleExecutors() {
        int r = 0;
        for (IComputer c : getComputers())
            if ((c.isOnline() || c.isConnecting()) && c.isAcceptingTasks())
                r += c.countIdle();
        return r;
    }

    @Override
    public String getSearchUrl() {
        return "/computers/";
    }

    public Computer getDynamic(String token, StaplerRequest2 req, StaplerResponse2 rsp) {
        return Jenkins.get().getComputer(token);
    }

    @RequirePOST
    public void do_launchAll(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        for (IComputer c : getComputers()) {
            if (c.isLaunchSupported())
                c.connect(true);
        }
        rsp.sendRedirect(".");
    }

    /**
     * Triggers the schedule update now.
     *
     * TODO: ajax on the client side to wait until the update completion might be nice.
     */
    @RequirePOST
    public void doUpdateNow(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.MANAGE);

        for (NodeMonitor nodeMonitor : NodeMonitor.getAll()) {
            Thread t = nodeMonitor.triggerUpdate();
            String columnCaption = nodeMonitor.getColumnCaption();
            if (columnCaption != null) {
                t.setName(columnCaption);
            }
        }
        rsp.forwardToPreviousPage(req);
    }

    /**
     * First check point in creating a new agent.
     */
    @RequirePOST
    public synchronized void doCreateItem(StaplerRequest2 req, StaplerResponse2 rsp,
                                           @QueryParameter String name, @QueryParameter String mode,
                                           @QueryParameter String from) throws IOException, ServletException {
        final Jenkins app = Jenkins.get();
        app.checkPermission(Computer.CREATE);

        if (mode != null && mode.equals("copy")) {
            name = checkName(name);

            Node src = app.getNode(from);
            if (src == null) {
                if (Util.fixEmpty(from) == null) {
                    throw new Failure(Messages.ComputerSet_SpecifySlaveToCopy());
                } else {
                    throw new Failure(Messages.ComputerSet_NoSuchSlave(from));
                }
            }

            // copy through XStream
            String xml = Jenkins.XSTREAM.toXML(src);
            Node result = (Node) Jenkins.XSTREAM.fromXML(xml);
            result.setNodeName(name);
            result.holdOffLaunchUntilSave = true;

            app.addNode(result);

            // send the browser to the config page
            rsp.sendRedirect2(result.getNodeName() + "/configure");
        } else {
            // proceed to step 2
            if (mode == null) {
                throw new Failure("No mode given");
            }

            NodeDescriptor d = NodeDescriptor.all().findByName(mode);
            if (d == null) {
                throw new Failure("No node type ‘" + mode + "’ is known");
            }
            d.handleNewNodePage(this, name, req, rsp);
        }
    }

    /**
     * Really creates a new agent.
     */
    @POST
    public synchronized void doDoCreateItem(StaplerRequest2 req, StaplerResponse2 rsp,
                                           @QueryParameter String name,
                                           @QueryParameter String type) throws IOException, ServletException, FormException {
        final Jenkins app = Jenkins.get();
        app.checkPermission(Computer.CREATE);
        String fixedName = Util.fixEmptyAndTrim(name);
        checkName(fixedName);

        JSONObject formData = req.getSubmittedForm();
        formData.put("name", fixedName);

        // TODO type is probably NodeDescriptor.id but confirm
        Node result = NodeDescriptor.all().find(type).newInstance(req, formData);
        app.addNode(result);

        // take the user back to the agent list top page
        rsp.sendRedirect2(".");
    }

    /**
     * Makes sure that the given name is good as an agent name.
     * @return trimmed name if valid; throws ParseException if not
     */
    public String checkName(String name) throws Failure {
        if (name == null)
            throw new Failure("Query parameter 'name' is required");

        name = name.trim();
        Jenkins.checkGoodName(name);

        if (Jenkins.get().getNode(name) != null)
            throw new Failure(Messages.ComputerSet_SlaveAlreadyExists(name));

        // looks good
        return name;
    }

    /**
     * Makes sure that the given name is good as an agent name.
     */
    public FormValidation doCheckName(@QueryParameter String value) throws IOException, ServletException {
        Jenkins.get().checkPermission(Computer.CREATE);

        if (Util.fixEmpty(value) == null)
            return FormValidation.ok();

        try {
            checkName(value);
            return FormValidation.ok();
        } catch (Failure e) {
            return FormValidation.error(e.getMessage());
        }
    }

    /**
     * Accepts submission from the configuration page.
     */
    @POST
    public synchronized HttpResponse doConfigSubmit(StaplerRequest2 req) throws IOException, ServletException, FormException {
        BulkChange bc = new BulkChange(MONITORS_OWNER);
        try {
            Jenkins.get().checkPermission(Jenkins.MANAGE);
            JSONObject json = req.getSubmittedForm();
            monitors.rebuild(req, json, getNodeMonitorDescriptors());

            // add in the rest of instances are ignored instances
            for (Descriptor<NodeMonitor> d : NodeMonitor.all()) {
                NodeMonitor monitor = monitors.get(d);
                if (monitor == null) {
                    NodeMonitor i = createDefaultInstance(d, true);
                    if (i != null)
                        monitors.add(i);
                } else {
                    /*
                     * Some monitors in plugins do not have a DataBoundConstructor
                     * but a Descriptor that overrides newInstance. For those the ignored
                     * field is not set, so we have to explicitly set it.
                     */
                    String name = d.getJsonSafeClassName();
                    JSONObject o = json.optJSONObject(name);
                    boolean ignored = true;
                    if (o != null) {
                        if (o.containsKey("ignored")) {
                            ignored = o.getBoolean("ignored");
                        }
                    }
                    monitor.setIgnored(ignored);
                }
            }

            // recompute the data
            for (NodeMonitor nm : monitors) {
                nm.triggerUpdate();
            }

            return FormApply.success(".");
        } finally {
            bc.commit();
        }
    }

    /**
     * {@link NodeMonitor}s are persisted in this file.
     */
    private static XmlFile getConfigFile() {
        return new XmlFile(new File(Jenkins.get().getRootDir(), "nodeMonitors.xml"));
    }

    public Api getApi() {
        return new Api(this);
    }

    @Override
    public Descriptor<ComputerSet> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(ComputerSet.class);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ComputerSet> {
        /**
         * Auto-completion for the "copy from" field in the new job page.
         */
        public AutoCompletionCandidates doAutoCompleteCopyNewItemFrom(@QueryParameter final String value) {
            final AutoCompletionCandidates r = new AutoCompletionCandidates();

            for (Node n : Jenkins.get().getNodes()) {
                if (n.getNodeName().startsWith(value))
                    r.add(n.getNodeName());
            }

            return r;
        }
    }

    /**
     * Just to force the execution of the static initializer.
     */
    public static void initialize() {}

    @Initializer(after = JOB_CONFIG_ADAPTED)
    public static void init() {
        // start monitoring nodes, although there's no hurry.
        Timer.get().schedule(new SafeTimerTask() {
            @Override
            public void doRun() {
                ComputerSet.initialize();
            }
        }, 10, TimeUnit.SECONDS);
    }

    /**
     * @return The list of strings of computer names (excluding master)
     * @since 2.14
     */
    @NonNull
    public static List<String> getComputerNames() {
        final ArrayList<String> names = new ArrayList<>();
        for (Computer c : Jenkins.get().getComputers()) {
            if (!c.getName().isEmpty()) {
                names.add(c.getName());
            }
        }
        return names;
    }

    private static final Logger LOGGER = Logger.getLogger(ComputerSet.class.getName());

    static {
        try {
            DescribableList<NodeMonitor, Descriptor<NodeMonitor>> r
                    = new DescribableList<>(Saveable.NOOP);

            // load persisted monitors
            XmlFile xf = getConfigFile();
            if (xf.exists()) {
                DescribableList<NodeMonitor, Descriptor<NodeMonitor>> persisted =
                        (DescribableList<NodeMonitor, Descriptor<NodeMonitor>>) xf.read();
                List<NodeMonitor> sanitized = new ArrayList<>();
                for (NodeMonitor nm : persisted) {
                    try {
                        nm.getDescriptor();
                        sanitized.add(nm);
                    } catch (Throwable e) {
                        // the descriptor didn't load? see JENKINS-15869
                    }
                }
                r.replaceBy(sanitized);
            }

            // if we have any new monitors, let's add them
            for (Descriptor<NodeMonitor> d : NodeMonitor.all())
                if (r.get(d) == null) {
                    NodeMonitor i = createDefaultInstance(d, false);
                    if (i != null)
                        r.add(i);
                }
            monitors.replaceBy(r.toList());
        } catch (Throwable x) {
            LOGGER.log(Level.WARNING, "Failed to instantiate NodeMonitors", x);
        }
    }

    private static NodeMonitor createDefaultInstance(Descriptor<NodeMonitor> d, boolean ignored) {
        try {
            NodeMonitor nm = d.clazz.getDeclaredConstructor().newInstance();
            nm.setIgnored(ignored);
            return nm;
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            LOGGER.log(Level.SEVERE, "Failed to instantiate " + d.clazz, e);
        }
        return null;
    }

    @Extension(ordinal = -1)
    public static class ComputerSourceImpl implements ComputerSource {
        @Override
        public Collection<? extends IComputer> get() {
            return Jenkins.get().getComputersCollection();
        }
    }
}
