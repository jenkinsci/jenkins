/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jorg Heymans, Peter Hayes, Red Hat, Inc., Stephen Connolly, id:cactusman
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
package hudson.maven;

import hudson.*;
import hudson.model.*;
import hudson.model.Descriptor.FormException;
import static hudson.model.ItemGroupMixIn.loadChildren;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import hudson.search.CollectionSearchIndex;
import hudson.search.SearchIndexBuilder;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tasks.*;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.util.CopyOnWriteMap;
import hudson.util.DescribableList;
import hudson.util.Function1;
import hudson.util.FormValidation;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.*;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.export.Exported;

/**
 * Group of {@link MavenModule}s.
 *
 * <p>
 * This corresponds to the group of Maven POMs that constitute a single
 * tree of projects. This group serves as the grouping of those related
 * modules.
 *
 * @author Kohsuke Kawaguchi
 */
public final class MavenModuleSet extends AbstractMavenProject<MavenModuleSet,MavenModuleSetBuild> implements TopLevelItem, ItemGroup<MavenModule>, SCMedItem, Saveable {
    /**
     * All {@link MavenModule}s, keyed by their {@link MavenModule#getModuleName()} module name}s.
     */
    transient /*final*/ Map<ModuleName,MavenModule> modules = new CopyOnWriteMap.Tree<ModuleName,MavenModule>();

    /**
     * Topologically sorted list of modules. This only includes live modules,
     * since archived ones usually don't have consistent history.
     */
    @CopyOnWrite
    transient List<MavenModule> sortedActiveModules;

    /**
     * Name of the top-level module. Null until the root module is determined.
     */
    private ModuleName rootModule;

    private String rootPOM;

    private String goals;

    /**
     * Default goals specified in POM. Can be null.
     */
    private String defaultGoals;

    /**
     * Identifies {@link MavenInstallation} to be used.
     * Null to indicate 'default' maven.
     */
    private String mavenName;

    /**
     * Equivalent of CLI <tt>MAVEN_OPTS</tt>. Can be null.
     */
    private String mavenOpts;

    /**
     * If true, the build will be aggregator style, meaning
     * all the modules are executed in a single Maven invocation, as in CLI.
     * False otherwise, meaning each module is built separately and possibly in parallel.
     *
     * @since 1.133
     */
    private boolean aggregatorStyleBuild = true;

    /**
     * If true, the build will use its own local Maven repository
     * via "-Dmaven.repo.local=...".
     * <p>
     * This would consume additional disk space, but provides isolation with other builds on the same machine,
     * such as mixing SNAPSHOTS. Maven also doesn't try to coordinate the concurrent access to Maven repositories
     * from multiple Maven process, so this helps there too.
     *
     * @since 1.223
     */
    private boolean usePrivateRepository = false;

    /**
     * If true, do not automatically schedule a build when one of the project dependencies is built.
     * <p>
     * See HUDSON-1714.
     */
    private boolean ignoreUpstremChanges = false;

    /**
     * Reporters configured at {@link MavenModuleSet} level. Applies to all {@link MavenModule} builds.
     */
    private DescribableList<MavenReporter,Descriptor<MavenReporter>> reporters =
        new DescribableList<MavenReporter,Descriptor<MavenReporter>>(this);

    /**
     * List of active {@link Publisher}s configured for this project.
     * @since 1.176
     */
    private DescribableList<Publisher,Descriptor<Publisher>> publishers =
        new DescribableList<Publisher,Descriptor<Publisher>>(this);

    /**
     * List of active ${link BuildWrapper}s configured for this project.
     * @since 1.212
     */
    private DescribableList<BuildWrapper,Descriptor<BuildWrapper>> buildWrappers =
        new DescribableList<BuildWrapper, Descriptor<BuildWrapper>>(this);

    public MavenModuleSet(String name) {
        super(Hudson.getInstance(),name);
    }

    public String getUrlChildPrefix() {
        // seemingly redundant "./" is used to make sure that ':' is not interpreted as the scheme identifier
        return ".";
    }

    public Hudson getParent() {
        return Hudson.getInstance();
    }

    public Collection<MavenModule> getItems() {
        return modules.values();
    }

    @Exported
    public Collection<MavenModule> getModules() {
        return getItems();
    }

    public MavenModule getItem(String name) {
        return modules.get(ModuleName.fromString(name));
    }

    public MavenModule getModule(String name) {
        return getItem(name);
    }

    protected void updateTransientActions() {
        super.updateTransientActions();
        // Fix for ISSUE-1149
        for (MavenModule module: modules.values()) {
            module.updateTransientActions();
        }
        if(publishers!=null)    // this method can be loaded from within the onLoad method, where this might be null
            for (BuildStep step : publishers) {
                Action a = step.getProjectAction(this);
                if(a!=null)
                    transientActions.add(a);
            }

        if (buildWrappers!=null)
	        for (BuildWrapper step : buildWrappers) {
	            Action a = step.getProjectAction(this);
	            if(a!=null)
	                transientActions.add(a);
	        }
    }

    protected void addTransientActionsFromBuild(MavenModuleSetBuild build, Set<Class> added) {
        if(build==null)    return;

        for (Action a : build.getActions())
            if(a instanceof MavenAggregatedReport)
                if(added.add(a.getClass()))
                    transientActions.add(((MavenAggregatedReport)a).getProjectAction(this));

        List<MavenReporter> list = build.projectActionReporters;
        if(list==null)   return;

        for (MavenReporter step : list) {
            if(!added.add(step.getClass()))     continue;   // already added
            Action a = step.getAggregatedProjectAction(this);
            if(a!=null)
                transientActions.add(a);
        }
    }

    /**
     * Called by {@link MavenModule#doDoDelete(StaplerRequest, StaplerResponse)}.
     * Real deletion is done by the caller, and this method only adjusts the
     * data structure the parent maintains.
     */
    /*package*/ void onModuleDeleted(MavenModule module) {
        modules.remove(module.getModuleName());
    }

    /**
     * Returns true if there's any disabled module.
     */
    public boolean hasDisabledModule() {
        for (MavenModule m : modules.values()) {
            if(m.isDisabled())
                return true;
        }
        return false;
    }

    /**
     * Possibly empty list of all disabled modules (if disabled==true)
     * or all enabeld modules (if disabled==false)
     */
    public List<MavenModule> getDisabledModules(boolean disabled) {
        if(!disabled && sortedActiveModules!=null)
            return sortedActiveModules;

        List<MavenModule> r = new ArrayList<MavenModule>();
        for (MavenModule m : modules.values()) {
            if(m.isDisabled()==disabled)
                r.add(m);
        }
        return r;
    }

    public Indenter<MavenModule> createIndenter() {
        return new Indenter<MavenModule>() {
            protected int getNestLevel(MavenModule job) {
                return job.nestLevel;
            }
        };
    }

    public boolean isAggregatorStyleBuild() {
        return aggregatorStyleBuild;
    }

    public boolean usesPrivateRepository() {
        return usePrivateRepository;
    }

    public boolean ignoreUpstremChanges() {
        return ignoreUpstremChanges;
    }

    /**
     * List of active {@link MavenReporter}s that should be applied to all module builds.
     */
    public DescribableList<MavenReporter, Descriptor<MavenReporter>> getReporters() {
        return reporters;
    }

    /**
     * List of active {@link Publisher}s. Can be empty but never null.
     */
    public DescribableList<Publisher, Descriptor<Publisher>> getPublishers() {
        return publishers;
    }

    @Override
    public DescribableList<Publisher, Descriptor<Publisher>> getPublishersList() {
        return publishers;
    }

    /**
     * List of active {@link BuildWrapper}s. Can be empty but never null.
     */
    public DescribableList<BuildWrapper, Descriptor<BuildWrapper>> getBuildWrappers() {
        return buildWrappers;
    }

    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        if(ModuleName.isValid(token))
            return getModule(token);
        return super.getDynamic(token,req,rsp);
    }

    public File getRootDirFor(MavenModule child) {
        return new File(getModulesDir(),child.getModuleName().toFileSystemName());
    }

    public Collection<Job> getAllJobs() {
        Set<Job> jobs = new HashSet<Job>(getItems());
        jobs.add(this);
        return jobs;
    }

    /**
     * Gets the workspace of this job.
     */
    public FilePath getWorkspace() {
        Node node = getLastBuiltOn();
        if(node==null)  node = Hudson.getInstance();
        return node.getWorkspaceFor(this);
    }

    @Override
    protected Class<MavenModuleSetBuild> getBuildClass() {
        return MavenModuleSetBuild.class;
    }

    @Override
    protected SearchIndexBuilder makeSearchIndex() {
        return super.makeSearchIndex()
            .add(new CollectionSearchIndex<MavenModule>() {// for computers
                protected MavenModule get(String key) {
                    for (MavenModule m : modules.values()) {
                        if(m.getDisplayName().equals(key))
                            return m;
                    }
                    return null;
                }
                protected Collection<MavenModule> all() {
                    return modules.values();
                }
                protected String getName(MavenModule o) {
                    return o.getName();
                }
            });
    }

    @Override
    public boolean isFingerprintConfigured() {
        return true;
    }

    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        modules = Collections.emptyMap(); // needed during load
        super.onLoad(parent, name);

        modules = loadChildren(this, getModulesDir(),new Function1<ModuleName,MavenModule>() {
            public ModuleName call(MavenModule module) {
                return module.getModuleName();
            }
        });
        // update the transient nest level field.
        MavenModule root = getRootModule();
        if(root!=null && root.getChildren()!=null) {
            List<MavenModule> sortedList = new ArrayList<MavenModule>();
            Stack<MavenModule> q = new Stack<MavenModule>();
            root.nestLevel = 0;
            q.push(root);
            while(!q.isEmpty()) {
                MavenModule p = q.pop();
                sortedList.add(p);
                List<MavenModule> children = p.getChildren();
                if(children!=null) {
                    for (MavenModule m : children)
                        m.nestLevel = p.nestLevel+1;
                    for( int i=children.size()-1; i>=0; i--)    // add them in the reverse order
                        q.push(children.get(i));
                }
            }
            this.sortedActiveModules = sortedList;
        } else {
            this.sortedActiveModules = getDisabledModules(false);
        }

        if(reporters==null)
            reporters = new DescribableList<MavenReporter, Descriptor<MavenReporter>>(this);
        reporters.setOwner(this);
        if(publishers==null)
            publishers = new DescribableList<Publisher,Descriptor<Publisher>>(this);
        publishers.setOwner(this);
        if(buildWrappers==null)
            buildWrappers = new DescribableList<BuildWrapper, Descriptor<BuildWrapper>>(this);
        buildWrappers.setOwner(this);

        updateTransientActions();
    }

    private File getModulesDir() {
        return new File(getRootDir(),"modules");
    }

    /**
     * To make it easy to grasp relationship among modules
     * and the module set, we'll align the build numbers of
     * all the modules.
     *
     * <p>
     * This method is invoked from {@link Executor#run()},
     * and because of the mutual exclusion among {@link MavenModuleSetBuild}
     * and {@link MavenBuild}, we can safely touch all the modules.
     */
    public synchronized int assignBuildNumber() throws IOException {
        // determine the next value
        updateNextBuildNumber();

        return super.assignBuildNumber();
    }

    public void logRotate() throws IOException, InterruptedException {
        super.logRotate();
        // perform the log rotation of modules
        for (MavenModule m : modules.values())
            m.logRotate();
    }

    /**
     * The next build of {@link MavenModuleSet} must have
     * the build number newer than any of the current module build.
     */
    /*package*/ void updateNextBuildNumber() throws IOException {
        int next = this.nextBuildNumber;
        for (MavenModule m : modules.values())
            next = Math.max(next,m.getNextBuildNumber());

        if(this.nextBuildNumber!=next) {
            this.nextBuildNumber=next;
            this.saveNextBuildNumber();
        }
    }

    protected void buildDependencyGraph(DependencyGraph graph) {
        publishers.buildDependencyGraph(this,graph);
        buildWrappers.buildDependencyGraph(this,graph);
    }

    public MavenModule getRootModule() {
        if(rootModule==null)    return null;
        return modules.get(rootModule);
    }

    public MavenInstallation inferMavenInstallation() {
        return getMaven();
    }

    @Override
    protected Set<ResourceActivity> getResourceActivities() {
        final Set<ResourceActivity> activities = new HashSet<ResourceActivity>();

        activities.addAll(super.getResourceActivities());
        activities.addAll(Util.filter(publishers,ResourceActivity.class));
        activities.addAll(Util.filter(buildWrappers,ResourceActivity.class));

        return activities;
    }

    /**
     * Gets the location of top-level <tt>pom.xml</tt> relative to the workspace root.
     */
    public String getRootPOM() {
        if(rootPOM==null)   return "pom.xml";
        return rootPOM;
    }

    public void setRootPOM(String rootPOM) {
        this.rootPOM = rootPOM;
    }

    public AbstractProject<?,?> asProject() {
        return this;
    }

    /**
     * Gets the list of goals to execute.
     */
    public String getGoals() {
        if(goals==null) {
            if(defaultGoals!=null)  return defaultGoals;
            return "install";
        }
        return goals;
    }

    public void setGoals(String goals) {
        this.goals = goals;
    }

    private List<String> getMavenArgument(String shortForm, String longForm) {
        List<String> args = new ArrayList<String>();
        boolean switchFound=false;
        for (String t : Util.tokenize(getGoals())) {
            if(switchFound) {
                args.add(t);
                switchFound = false;
            }
            else
            if(t.equals(shortForm) || t.equals(longForm))
                switchFound=true;
            else
            if(t.startsWith(shortForm)) {
                args.add(t.substring(shortForm.length()));
            }
            else
            if(t.startsWith(longForm)) {
                args.add(t.substring(longForm.length()));
            }
        }
        return args;
    }

    /**
     * If the list of configured goals contain the "-P" option,
     * return the configured profiles. Otherwise null.
     */
    public String getProfiles() {
        return Util.join(getMavenArgument("-P","--activate-profiles"),",");
    }

    /**
     * Gets the system properties explicitly set in the Maven command line (the "-D" option.)
     */
    public Properties getMavenProperties() {
        Properties props = new Properties();
        for (String arg : getMavenArgument("-D","--define")) {
            int idx = arg.indexOf('=');
            if(idx<0)   props.put(arg,"true");
            else        props.put(arg.substring(0,idx),arg.substring(idx+1));
        }
        return props;
    }

    /**
     * Possibly null, whitespace-separated (including TAB, NL, etc) VM options
     * to be used to launch Maven process.
     */
    public String getMavenOpts() {
        return mavenOpts;
    }

    /**
     * Gets the Maven to invoke.
     * If null, we pick any random Maven installation.
     */
    public MavenInstallation getMaven() {
        for( MavenInstallation i : DESCRIPTOR.getMavenDescriptor().getInstallations() ) {
            if(mavenName==null || i.getName().equals(mavenName))
                return i;
        }
        return null;
    }

    public void setMaven(String mavenName) {
        this.mavenName = mavenName;
    }

    /**
     * Returns the {@link MavenModule}s that are in the queue.
     */
    public List<Queue.Item> getQueueItems() {
        List<Queue.Item> r = new ArrayList<hudson.model.Queue.Item>();
        for( Queue.Item item : Hudson.getInstance().getQueue().getItems() ) {
            Task t = item.task;
            if((t instanceof MavenModule && ((MavenModule)t).getParent()==this) || t ==this)
                r.add(item);
        }
        return r;
    }

    /**
     * Gets the list of goals specified by the user,
     * without taking inheritance and POM default goals
     * into account.
     *
     * <p>
     * This is only used to present the UI screen, and in
     * all the other cases {@link #getGoals()} should be used.
     */
    public String getUserConfiguredGoals() {
        return goals;
    }

    /*package*/ void reconfigure(PomInfo rootPom) throws IOException {
        if(this.rootModule!=null && this.rootModule.equals(rootPom.name))
            return; // no change
        this.rootModule = rootPom.name;
        this.defaultGoals = rootPom.defaultGoal;
        save();
    }

//
//
// Web methods
//
//

    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        super.submit(req,rsp);
        JSONObject json = req.getSubmittedForm();

        rootPOM = Util.fixEmpty(req.getParameter("rootPOM").trim());
        if(rootPOM!=null && rootPOM.equals("pom.xml"))   rootPOM=null;   // normalization

        goals = Util.fixEmpty(req.getParameter("goals").trim());
        mavenOpts = Util.fixEmpty(req.getParameter("mavenOpts").trim());
        mavenName = req.getParameter("maven_version");
        aggregatorStyleBuild = !req.hasParameter("maven.perModuleBuild");
        usePrivateRepository = req.hasParameter("maven.usePrivateRepository");
        ignoreUpstremChanges = !json.has("triggerByDependency");

        reporters.rebuild(req,json,MavenReporters.getConfigurableList());
        publishers.rebuild(req,json,BuildStepDescriptor.filter(Publisher.all(),this.getClass()));
        buildWrappers.rebuild(req,json,BuildWrappers.getFor(this));

        updateTransientActions(); // to pick up transient actions from builder, publisher, etc.
    }

    /**
     * Delete all disabled modules.
     */
    public void doDoDeleteAllDisabledModules(StaplerResponse rsp) throws IOException, InterruptedException {
        checkPermission(DELETE);
        for( MavenModule m : getDisabledModules(true))
            m.delete();
        rsp.sendRedirect2(".");
    }

    /**
     * Check the location of POM.
     */
    public FormValidation doCheckRootPOM(@QueryParameter String value) throws IOException, ServletException {
        FilePath ws = getModuleRoot();
        if(ws==null) return FormValidation.ok();
        return ws.validateRelativePath(value,true,true);
    }

    public TopLevelItemDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    @Extension(ordinal=900)
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends AbstractProjectDescriptor {
        public String getDisplayName() {
            return Messages.MavenModuleSet_DiplayName();
        }

        public MavenModuleSet newInstance(String name) {
            return new MavenModuleSet(name);
        }

        public Maven.DescriptorImpl getMavenDescriptor() {
            return Hudson.getInstance().getDescriptorByType(Maven.DescriptorImpl.class);
        }

        @Override
        public boolean isApplicable(Descriptor descriptor) {
            return !NOT_APPLICABLE_TYPES.contains(descriptor.clazz);
        }

        private static final Set<Class> NOT_APPLICABLE_TYPES = new HashSet<Class>(Arrays.asList(
            ArtifactArchiver.class, // this happens automatically
            Fingerprinter.class,    // this kicks in automatically
            JavadocArchiver.class,  // this kicks in automatically
            Mailer.class,           // for historical reasons, Maven uses MavenMailer
            JUnitResultArchiver.class // done by SurefireArchiver
        ));
    }
}
