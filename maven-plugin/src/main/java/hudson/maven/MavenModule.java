/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, id:cactusman
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import hudson.CopyOnWrite;
import hudson.Functions;
import hudson.Util;
import hudson.maven.reporters.MavenMailer;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.JDK;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Resource;
import hudson.model.Saveable;
import hudson.tasks.LogRotator;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tasks.Publisher;
import hudson.util.AlternativeUiTextProvider;
import hudson.util.DescribableList;
import jenkins.model.Jenkins;
import org.apache.maven.project.MavenProject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Job} that builds projects based on Maven2.
 * 
 * @author Kohsuke Kawaguchi
 */
public class MavenModule extends AbstractMavenProject<MavenModule,MavenBuild> implements Saveable {
    private DescribableList<MavenReporter,Descriptor<MavenReporter>> reporters =
        new DescribableList<MavenReporter,Descriptor<MavenReporter>>(this);


    /**
     * Name taken from {@link MavenProject#getName()}.
     */
    private String displayName;

    /**
     * Version number of this module as of the last build, taken from {@link MavenProject#getVersion()}.
     *
     * This field can be null if Jenkins loaded old data
     * that didn't record this information, so that situation
     * needs to be handled gracefully.
     * 
     * @since 1.199
     */
    private String version;
    
    /**
     * Packaging type of the module.
     * 
     * pom, jar, maven-plugin, ejb, war, ear, rar, par or other custom types.
     * 
     * @since 1.425
     */
    private String packaging;

    private transient ModuleName moduleName;

    /**
     * @see PomInfo#relativePath
     */
    private String relativePath;

    /**
     * If this module has goals specified by itself.
     * Otherwise leave it null to use the default goals specified in the parent.
     */
    private String goals;

    /**
     * List of modules that this module declares direct dependencies on.
     */
    @CopyOnWrite
    private volatile Set<ModuleDependency> dependencies;

    /**
     * List of child modules as defined by &lt;module> POM element.
     * Used to determine parent/child relationship of modules.
     * <p>
     * For compatibility reason, this field may be null when loading data from old hudson.
     * 
     * @since 1.133
     */
    @CopyOnWrite
    private volatile List<ModuleName> children;

    /**
     * Nest level used to display this module in the module list.
     * The root module and orphaned module gets 0.
     */
    /*package*/ volatile transient int nestLevel;

    /*package*/ MavenModule(MavenModuleSet parent, PomInfo pom, int firstBuildNumber) throws IOException {
        super(parent, pom.name.toFileSystemName());
        reconfigure(pom);
        updateNextBuildNumber(firstBuildNumber);
    }

    /**
     * {@link MavenModule} follows the same log rotation schedule as its parent. 
     */
    @Override
    public LogRotator getLogRotator() {
        return getParent().getLogRotator();
    }

    /**
     * @deprecated
     *      Not allowed to configure log rotation per module.
     */
    @Override
    public void setLogRotator(LogRotator logRotator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsLogRotator() {
        return false;
    }

    @Override
    public boolean isBuildable() {
        // not buildable if the parent project is disabled
        return super.isBuildable() && getParent().isBuildable();
    }

    /**
     * Computes the list of {@link MavenModule}s that are 'under' this POM filesystem-wise. The list doens't include
     * this module itself.
     *
     * <p>
     * Note that this doesn't necessary has anything to do with the module inheritance structure or parent/child
     * relationship of the POM.
     */
    public List<MavenModule> getSubsidiaries() {
        List<MavenModule> r = new ArrayList<MavenModule>();
        for (MavenModule mm : getParent().getModules())
            if(mm!=this && mm.getRelativePath().startsWith(getRelativePath()))
                r.add(mm);
        return r;
    }

    /**
     * Called to update the module with the new POM.
     * <p>
     * This method is invoked on {@link MavenModule} that has the matching
     * {@link ModuleName}.
     */
    /*package*/ void reconfigure(PomInfo pom) {
        this.displayName = pom.displayName;
        this.version = pom.version;
        this.packaging = pom.packaging;
        this.relativePath = pom.relativePath;
        this.dependencies = pom.dependencies;
        this.children = pom.children;
        this.nestLevel = pom.getNestLevel();
        disabled = false;

        if (pom.mailNotifier != null) {
            MavenReporter reporter = getReporters().get(MavenMailer.class);
            if (reporter != null) {
                MavenMailer mailer = (MavenMailer) reporter;
                mailer.dontNotifyEveryUnstableBuild = !pom.mailNotifier.isSendOnFailure();
                String recipients = pom.mailNotifier.getConfiguration().getProperty("recipients");
                if (recipients != null) {
                    mailer.recipients = recipients;
                }
            }
        }
    }
    
    /**
     * Returns if the given POM likely describes the same module with the same dependencies.
     * Implementation needs not be 100% accurate in the true case, but it MUST return false
     * if is not the same.
     */
    public boolean isSameModule(PomInfo pom) {
        return pom.isSimilar(this.moduleName, this.dependencies);
    }

    @Override
    protected void doSetName(String name) {
        moduleName = ModuleName.fromFileSystemName(name);
        super.doSetName(moduleName.toString());
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent,name);
        if(reporters==null)
            reporters = new DescribableList<MavenReporter, Descriptor<MavenReporter>>(this);
        reporters.setOwner(this);
        if(dependencies==null)
            dependencies = Collections.emptySet();
        else {
            // Until 1.207, we used to have ModuleName in dependencies. So convert.
            Set<ModuleDependency> deps = new HashSet<ModuleDependency>(dependencies.size());
            for (Object d : (Set<?>)dependencies) {
                if (d instanceof ModuleDependency) {
                    deps.add((ModuleDependency) d);
                } else {
                    deps.add(new ModuleDependency((ModuleName)d, ModuleDependency.UNKNOWN, false));
                }
            }
            dependencies = deps;
        }
    }

    /**
     * Relative path to this module's root directory
     * from the workspace of a {@link MavenModuleSet}.
     *
     * The path separator is normalized to '/'.
     */
    public String getRelativePath() {
        return relativePath;
    }

    /**
     * Gets the version number in Maven POM as of the last build.
     *
     * @return
     *      This method can return null if Jenkins loaded old data
     *      that didn't record this information, so that situation
     *      needs to be handled gracefully.
     * @since 1.199
     */
    public String getVersion() {
        return version;
    }

    /**
     * Gets the list of goals to execute for this module.
     */
    public String getGoals() {
        if(goals!=null) return goals;
        return getParent().getGoals();
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

    public DescribableList<Publisher,Descriptor<Publisher>> getPublishersList() {
        // TODO
        return new DescribableList<Publisher,Descriptor<Publisher>>(this);
    }

    @Override
    public JDK getJDK() {
        // share one setting for the whole module set.
        return getParent().getJDK();
    }

    @Override
    protected Class<MavenBuild> getBuildClass() {
        return MavenBuild.class;
    }

    @Override
    protected MavenBuild newBuild() throws IOException {
        return super.newBuild();
    }

    public ModuleName getModuleName() {
        return moduleName;
    }

    /**
     * Gets groupId+artifactId+version as {@link ModuleDependency}.
     */
    public ModuleDependency asDependency() {
        return new ModuleDependency(moduleName,Functions.defaulted(version,ModuleDependency.UNKNOWN),
                PomInfo.PACKAGING_TYPE_PLUGIN.equals(this.packaging));
    }

    @Override
    public String getShortUrl() {
        return moduleName.toFileSystemName()+'/';
    }

    @Exported(visibility=2)
    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getPronoun() {
        return AlternativeUiTextProvider.get(PRONOUN, this, Messages.MavenModule_Pronoun());
    }

    @Override
    public boolean isNameEditable() {
        return false;
    }

    @Override
    public MavenModuleSet getParent() {
        return (MavenModuleSet)super.getParent();
    }

    /**
     * Gets all the child modules (that are listed in the &lt;module> element in our POM.)
     * <p>
     * This method returns null if this information is not recorded. This happens
     * for compatibility reason.
     * 
     * @since 1.133
     */
    public List<MavenModule> getChildren() {
        List<ModuleName> l = children;    // take a snapshot
        if(l==null) return null;

        List<MavenModule> modules = new ArrayList<MavenModule>(l.size());
        for (ModuleName n : l) {
            MavenModule m = getParent().modules.get(n);
            if(m!=null)
                modules.add(m);
        }
        return modules;
    }

    /**
     * {@link MavenModule} uses the workspace of the {@link MavenModuleSet},
     * so it always needs to be built on the same slave as the parent.
     */
    @Override
    public Label getAssignedLabel() {
        Node n = getParent().getLastBuiltOn();
        if(n==null) return null;
        return n.getSelfLabel();
    }

    /**
     * Workspace of a {@link MavenModule} is a part of the parent's workspace.
     * <p>
     * That is, {@Link MavenModuleSet} builds are incompatible with any {@link MavenModule}
     * builds, whereas {@link MavenModule} builds are compatible with each other.
     *
     * @deprecated as of 1.319 in {@link AbstractProject}.
     */
    @Override
    public Resource getWorkspaceResource() {
        return new Resource(getParent().getWorkspaceResource(),getDisplayName()+" workspace");
    }

    @Override
    public boolean isFingerprintConfigured() {
        return true;
    }

    @Override // to make this accessible to MavenModuleSet
    protected void updateTransientActions() {
        super.updateTransientActions();
    }

    protected void buildDependencyGraph(DependencyGraph graph) {
        if(!isBuildable() || getParent().ignoreUpstremChanges())        return;

        MavenDependencyComputationData data = graph.getComputationalData(MavenDependencyComputationData.class);

        // Build a map of all Maven modules in this Jenkins instance as dependencies.

        // When we load old data that doesn't record version in dependency, we'd like
        // to emulate the old behavior that tries to identify the upstream by ignoring the version.
        // Do this by putting groupId:artifactId:UNKNOWN to the modules list, but
        // ONLY if we find a such an old MavenModule in this Jenkins instance.
        final boolean hasDependenciesWithUnknownVersion = hasDependenciesWithUnknownVersion();
        if (data == null) {
            Map<ModuleDependency,MavenModule> modules = new HashMap<ModuleDependency,MavenModule>();
    
            for (MavenModule m : getAllMavenModules()) {
                if(!m.isBuildable())  continue;
                ModuleDependency moduleDependency = m.asDependency();
                MavenModule old = modules.get(moduleDependency);
                MavenModule relevant = chooseMoreRelevantModule(old, m);
                modules.put(moduleDependency, relevant);
                if (hasDependenciesWithUnknownVersion) {
                    modules.put(moduleDependency.withUnknownVersion(),relevant);
                }
            }
            data = new MavenDependencyComputationData(modules);
            data.withUnknownVersions = hasDependenciesWithUnknownVersion;
            graph.putComputationalData(MavenDependencyComputationData.class, data);
        } else {
            if (hasDependenciesWithUnknownVersion && !data.withUnknownVersions) {
                // found 'old' MavenModule: add dependencies with unknown versions now
                for (MavenModule m : getAllMavenModules()) {
                    if(m.isDisabled())  continue;
                    ModuleDependency moduleDependency = m.asDependency().withUnknownVersion();
                    data.allModules.put(moduleDependency,m);
                }
                data.withUnknownVersions = true;
            }
        }

        // Can lead to OOME, if remembered in the computational data and there are lot big multi-module projects
        // TODO: try to use soft references to clean the heap when needed
        Map<ModuleDependency,MavenModule> myParentsModules; // = data.modulesPerParent.get(getParent());
        
        //if (myParentsModules == null) {
            myParentsModules = new HashMap<ModuleDependency, MavenModule>();
            
            for (MavenModule m : getParent().getModules()) {
                if(m.isDisabled())  continue;
                ModuleDependency moduleDependency = m.asDependency();
                myParentsModules.put(moduleDependency,m);
                if (hasDependenciesWithUnknownVersion) {
                    myParentsModules.put(moduleDependency.withUnknownVersion(),m);
                }
            }
            
            //data.modulesPerParent.put(getParent(), myParentsModules);
        //}

        //Create a map of groupId:artifact id keys to modules for faster look ups in findMatchingDependentModule
        Multimap<ModuleName,ModuleDependency> mapModules = data.byName();

        for (ModuleDependency d : dependencies) {
            MavenModule src;

            // In case two modules with the same name are defined, modules in the same MavenModuleSet
            // take precedence.
            src = myParentsModules.get(d);

            // otherwise we can pick the module with the highest version number (within the constraint that
            // it satisfies 'd')
            if (src==null) {
                Collection<ModuleDependency> candidates = mapModules.get(d.getName());
                ModuleDependency winner = d.findHighestFrom(candidates);
                src = data.allModules.get(winner);
            }

            if(src!=null && !src.getParent().isDisableTriggerDownstreamProjects()) {
                DependencyGraph.Dependency dep = new MavenModuleDependency(nodeOf(src),nodeOf(this));
                if (!dep.pointsItself())
                    graph.addDependency(dep);
            }
        }
    }

    /**
     * Determines the source/sink of the dependency from a module.
     * This is because if the build is the aggregator build, we need to define dependencies against project,
     * not module.
     */
    private static AbstractMavenProject<?, ?> nodeOf(MavenModule m) {
        return m.getParent().isAggregatorStyleBuild() ? m.getParent() : m;
    }
    
    /**
     * Returns all Maven modules in this Jenkins instance.
     */
    protected Collection<MavenModule> getAllMavenModules() {
        return Jenkins.getInstance().getAllItems(MavenModule.class);
    }
    
    /**
     * Check if this module has dependencies recorded without a concrete version -
     * which shouldn't happen for any module which was at least build once with Jenkins >= 1.207. 
     */
    private boolean hasDependenciesWithUnknownVersion() {
        for (ModuleDependency dep : dependencies) {
            if (ModuleDependency.UNKNOWN.equals(dep.version)) {
                return true;
            }
        }
        return false;
    }
    
    private MavenModule chooseMoreRelevantModule(MavenModule mm1, MavenModule mm2) {
        
        if (mm1 == null) {
            return mm2;
        }
        if (mm2 == null) {
            return mm1;
        }

        int score = mm1.getDependencyRelevancy() - mm2.getDependencyRelevancy();
        if (score==0) {
            // tie breaker. this is arbitrary, but reproduceable
            score = mm1.getParent().getFullName().compareTo(mm2.getParent().getFullName());
        }
        assert score!=0;

        final MavenModule moreRelevant, lessRelevant;

        if (score>0)    { moreRelevant = mm1; lessRelevant = mm2; }
        else            { moreRelevant = mm2; lessRelevant = mm1; }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.finer("Choosing " + moreRelevant.getParent().getName() + " over " + lessRelevant.getParent().getName()
                    + " for module " + mm1.asDependency().getName() + ". Relevancies: " + mm1.getDependencyRelevancy() + ", " + mm2.getDependencyRelevancy());
        }
        return moreRelevant;
    }

    /**
     * As a guide for automatic dependency computation,
     * determine how much this particular build is "relevant" to other builds on this Jenkins.
     *
     * If the binary is being deployed, we assume the user intends the result of this build be used elsewhere,
     * so we give a higher score.
     */
    private int getDependencyRelevancy() {
        int relevancy = 0;
        
        for (String goal : Util.tokenize(getGoals())) {
            if ("deploy".equals(goal) || "deploy:deploy".equals(goal)) {
                return 2;
            }
            
            if ("install".equals(goal)) {
                relevancy = 1;
            }
        }
        
        for (Publisher publisher : getParent().getPublishers()) {
            if (publisher instanceof RedeployPublisher) {
                return 2;
            }
        }
        
        return relevancy;
    }

    private static class MavenDependencyComputationData {
        boolean withUnknownVersions = false;
        /**
         * All {@link MavenModule}s in this Jenkins, keyed by their {@link MavenModule#asDependency()}.
         */
        private final Map<ModuleDependency,MavenModule> allModules;
        
        //Map<MavenModuleSet, Map<ModuleDependency,MavenModule>> modulesPerParent = new HashMap<MavenModuleSet, Map<ModuleDependency,MavenModule>>();
        
        public MavenDependencyComputationData(Map<ModuleDependency, MavenModule> modules) {
            this.allModules = modules;
        }

        /**
         * Builds a map of all the modules, keyed against the groupId and artifactId. The values are a list of modules
         * that match this criteria.
         *
         * @return {@link #allModules} keyed by their {@linkplain ModuleName names}.
         */
        private Multimap<ModuleName,ModuleDependency> byName() {
            Multimap<ModuleName,ModuleDependency> map = HashMultimap.create();

            for (ModuleDependency dependency : allModules.keySet()) {
                map.put(dependency.getName(),dependency);
            }

            return map;
        }
    }

    @Override
    protected void addTransientActionsFromBuild(MavenBuild build, List<Action> collection, Set<Class> added) {
        if(build==null)    return;
        List<MavenProjectActionBuilder> list = build.projectActionReporters;
        if(list==null)   return;

        for (MavenProjectActionBuilder step : list) {
            if(!added.add(step.getClass()))     continue;   // already added
            try {
                collection.addAll(step.getProjectActions(this));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to getProjectAction from " + step
                           + ". Report issue to plugin developers.", e);
            }
        }
    }

    public MavenInstallation inferMavenInstallation() {
        return getParent().inferMavenInstallation();
    }

    /**
     * List of active {@link MavenReporter}s configured for this module.
     */
    public DescribableList<MavenReporter, Descriptor<MavenReporter>> getReporters() {
        return reporters;
    }

    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        super.submit(req, rsp);

        reporters.rebuild(req, req.getSubmittedForm(),MavenReporters.getConfigurableList());

        goals = Util.fixEmpty(req.getParameter("goals").trim());

        // dependency setting might have been changed by the user, so rebuild.
        Jenkins.getInstance().rebuildDependencyGraphAsync();
    }

    @Override
    protected void performDelete() throws IOException, InterruptedException {
        super.performDelete();
        getParent().onModuleDeleted(this);
    }

    /**
     * Creates a list of {@link MavenReporter}s to be used for a build of this project.
     */
    protected List<MavenReporter> createReporters() {
        List<MavenReporter> reporterList = new ArrayList<MavenReporter>();

        getReporters().addAllTo(reporterList);
        getParent().getReporters().addAllTo(reporterList);

        for (MavenReporterDescriptor d : MavenReporterDescriptor.all()) {
            if(getReporters().contains(d))
                continue;   // already configured
            MavenReporter auto = d.newAutoInstance(this);
            if(auto!=null)
                reporterList.add(auto);
        }

        return reporterList;
    }
    
    /**
     * for debug purpose
     */
    public String toString() {
        return super.toString()+'['+getFullName()+']'+"[relativePath:"+getRelativePath()+']';
    }

    private static final Logger LOGGER = Logger.getLogger(MavenModule.class.getName());

}
