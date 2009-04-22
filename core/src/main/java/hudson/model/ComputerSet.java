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

import hudson.BulkChange;
import hudson.DescriptorExtensionList;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.Descriptor.FormException;
import hudson.node_monitors.NodeMonitor;
import hudson.slaves.NodeDescriptor;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.servlet.ServletException;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serves as the top of {@link Computer}s in the URL hierarchy.
 * <p>
 * Getter methods are prefixed with '_' to avoid collision with computer names.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public final class ComputerSet extends AbstractModelObject {
    /**
     * This is the owner that persists {@link #monitors}.
     */
    private static final Saveable MONITORS_OWNER = new Saveable() {
        public void save() throws IOException {
            getConfigFile().write(monitors);
        }
    };

    private static final DescribableList<NodeMonitor,Descriptor<NodeMonitor>> monitors
            = new DescribableList<NodeMonitor, Descriptor<NodeMonitor>>(MONITORS_OWNER);

    @Exported
    public String getDisplayName() {
        return "nodes";
    }

    /**
     * @deprecated as of 1.301
     *      Use {@link #getMonitors()}.
     */
    public static List<NodeMonitor> get_monitors() {
        return monitors.toList();
    }

    @Exported(name="computer",inline=true)
    public Computer[] get_all() {
        return Hudson.getInstance().getComputers();
    }

    /**
     * Exposing {@link NodeMonitor#all()} for Jelly binding.
     */
    public DescriptorExtensionList<NodeMonitor,Descriptor<NodeMonitor>> getNodeMonitorDescriptors() {
        return NodeMonitor.all();
    }

    public static DescribableList<NodeMonitor,Descriptor<NodeMonitor>> getMonitors() {
        return monitors;
    }

    /**
     * Returns a subset pf {@link #getMonitors()} that are {@linkplain NodeMonitor#isIgnored() not ignored}.
     */
    public static Map<Descriptor<NodeMonitor>,NodeMonitor> getNonIgnoredMonitors() {
        Map<Descriptor<NodeMonitor>,NodeMonitor> r = new HashMap<Descriptor<NodeMonitor>, NodeMonitor>();
        for (NodeMonitor m : monitors) {
            if(!m.isIgnored())
                r.put(m.getDescriptor(),m);
        }
        return r;
    }

    /**
     * Gets all the slave names.
     */
    public List<String> get_slaveNames() {
        return new AbstractList<String>() {
            final List<Node> nodes = Hudson.getInstance().getNodes();

            public String get(int index) {
                return nodes.get(index).getNodeName();
            }

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
        int r=0;
        for (Computer c : get_all()) {
            if(c.isOnline())
                r += c.countExecutors();
        }
        return r;
    }

    /**
     * Number of busy {@link Executor}s that are carrying out some work right now.
     */
    @Exported
    public int getBusyExecutors() {
        int r=0;
        for (Computer c : get_all()) {
            if(c.isOnline())
                r += c.countBusy();
        }
        return r;
    }

    /**
     * {@code getTotalExecutors()-getBusyExecutors()}, plus executors that are being brought online.
     */
    public int getIdleExecutors() {
        int r=0;
        for (Computer c : get_all())
            if(c.isOnline() || c.isConnecting())
                r += c.countIdle();
        return r;
    }

    public String getSearchUrl() {
        return "/computers/";
    }

    public Computer getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        return Hudson.getInstance().getComputer(token);
    }

    public void do_launchAll(StaplerRequest req, StaplerResponse rsp) throws IOException {
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);

        for(Computer c : get_all()) {
            if(c.isLaunchSupported())
                c.connect(true);
        }
        rsp.sendRedirect(".");
    }

    /**
     * Triggers the schedule update now.
     *
     * TODO: ajax on the client side to wait until the update completion might be nice.
     */
    public void doUpdateNow( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
        
        for (NodeMonitor nodeMonitor : NodeMonitor.getAll()) {
            Thread t = nodeMonitor.triggerUpdate();
            t.setName(nodeMonitor.getColumnCaption());
        }
        rsp.forwardToPreviousPage(req);
    }

    /**
     * First check point in creating a new slave.
     */
    public synchronized void doCreateItem( StaplerRequest req, StaplerResponse rsp,
                                           @QueryParameter("name") String name, @QueryParameter("mode") String mode,
                                           @QueryParameter("from") String from ) throws IOException, ServletException {
        final Hudson app = Hudson.getInstance();
        app.checkPermission(Hudson.ADMINISTER);  // TODO: new permission?

        try {
            checkName(name);
        } catch (ParseException e) {
            rsp.setStatus(SC_BAD_REQUEST);
            sendError(e,req,rsp);
            return;
        }

        if(mode!=null && mode.equals("copy")) {
            Node src = app.getNode(from);
            if(src==null) {
                rsp.setStatus(SC_BAD_REQUEST);
                if(Util.fixEmpty(from)==null)
                    sendError(Messages.ComputerSet_SpecifySlaveToCopy(),req,rsp);
                else
                    sendError(Messages.ComputerSet_NoSuchSlave(from),req,rsp);
                return;
            }

            // copy through XStream
            String xml = Hudson.XSTREAM.toXML(src);
            Node result = (Node)Hudson.XSTREAM.fromXML(xml);
            result.setNodeName(name);

            app.addNode(result);

            // send the browser to the config page
            rsp.sendRedirect2(result.getNodeName()+"/configure");
        } else {
            // proceed to step 2
            if(mode==null) {
                rsp.sendError(SC_BAD_REQUEST);
                return;
            }

            req.setAttribute("descriptor", NodeDescriptor.all().find(mode));
            req.getView(this,"_new.jelly").forward(req,rsp);
        }
    }

    /**
     * Really creates a new slave.
     */
    public synchronized void doDoCreateItem( StaplerRequest req, StaplerResponse rsp,
                                           @QueryParameter("name") String name,
                                           @QueryParameter("type") String type ) throws IOException, ServletException {
        try {
            final Hudson app = Hudson.getInstance();
            app.checkPermission(Hudson.ADMINISTER);  // TODO: new permission?
            checkName(name);

            Node result = NodeDescriptor.all().find(type).newInstance(req, req.getSubmittedForm());
            app.addNode(result);

            // take the user back to the slave list top page
            rsp.sendRedirect2(".");
        } catch (ParseException e) {
            rsp.setStatus(SC_BAD_REQUEST);
            sendError(e,req,rsp);
        } catch (FormException e) {
            sendError(e,req,rsp);
        }
    }

    /**
     * Makes sure that the given name is good as a slave name.
     */
    private void checkName(String name) throws ParseException {
        if(name==null)
            throw new ParseException("Query parameter 'name' is required",0);

        name = name.trim();
        Hudson.checkGoodName(name);

        if(Hudson.getInstance().getNode(name)!=null)
            throw new ParseException(Messages.ComputerSet_SlaveAlreadyExists(name),0);

        // looks good
    }

    /**
     * Makes sure that the given name is good as a slave name.
     */
    public FormValidation doCheckName(@QueryParameter String value) throws IOException, ServletException {
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);

        if(Util.fixEmpty(value)==null)
            return FormValidation.ok();
        
        try {
            checkName(value);
            return FormValidation.ok();
        } catch (ParseException e) {
            return FormValidation.error(e.getMessage());
        }
    }

    /**
     * Accepts submission from the configuration page.
     */
    public final synchronized void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        BulkChange bc = new BulkChange(MONITORS_OWNER);
        try {
            Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
            monitors.rebuild(req,req.getSubmittedForm(),getNodeMonitorDescriptors());

            // add in the rest of instances are ignored instances
            for (Descriptor<NodeMonitor> d : NodeMonitor.all())
                if(monitors.get(d)==null) {
                    NodeMonitor i = createDefaultInstance(d, true);
                    if(i!=null)
                        monitors.add(i);
                }
            rsp.sendRedirect2(".");
        } catch (FormException e) {
            sendError(e,req,rsp);
        } finally {
            bc.commit();
        }
    }

    /**
     * {@link NodeMonitor}s are persisted in this file.
     */
    private static XmlFile getConfigFile() {
        return new XmlFile(new File(Hudson.getInstance().getRootDir(),"nodeMonitors.xml"));
    }

    public Api getApi() {
        return new Api(this);
    }

    /**
     * Just to force the execution of the static initializer.
     */
    public static void initialize() {}

    private static final Logger LOGGER = Logger.getLogger(ComputerSet.class.getName());

    static {
        try {
            DescribableList<NodeMonitor,Descriptor<NodeMonitor>> r
                    = new DescribableList<NodeMonitor, Descriptor<NodeMonitor>>(Saveable.NOOP);

            // load persisted monitors
            XmlFile xf = getConfigFile();
            if(xf.exists()) {
                DescribableList<NodeMonitor,Descriptor<NodeMonitor>> persisted =
                        (DescribableList<NodeMonitor,Descriptor<NodeMonitor>>) xf.read();
                r.replaceBy(persisted.toList());
            }

            // if we have any new monitors, let's add them
            for (Descriptor<NodeMonitor> d : NodeMonitor.all())
                if(r.get(d)==null) {
                    NodeMonitor i = createDefaultInstance(d,false);
                    if(i!=null)
                        r.add(i);
                }
            monitors.replaceBy(r.toList());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to instanciate NodeMonitors",e);
        }
    }

    private static NodeMonitor createDefaultInstance(Descriptor<NodeMonitor> d, boolean ignored) {
        try {
            NodeMonitor nm = d.clazz.newInstance();
            nm.setIgnored(ignored);
            return nm;
        } catch (InstantiationException e) {
            LOGGER.log(Level.SEVERE, "Failed to instanciate "+d.clazz,e);
        } catch (IllegalAccessException e) {
            LOGGER.log(Level.SEVERE, "Failed to instanciate "+d.clazz,e);
        }
        return null;
    }
}
