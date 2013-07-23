/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Jorg Heymans, Red Hat, Inc., id:cactusman
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
package hudson.matrix;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import hudson.CopyOnWrite;
import hudson.Extension;
import hudson.Util;
import hudson.XmlFile;
import hudson.matrix.MatrixBuild.MatrixBuildExecution;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Node;
import hudson.slaves.WorkspaceList;
import hudson.util.AlternativeUiTextProvider;
import jenkins.model.Jenkins;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.JDK;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue.FlyweightTask;
import hudson.model.Result;
import hudson.model.SCMedItem;
import hudson.model.Saveable;
import hudson.model.TopLevelItem;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;
import hudson.util.CopyOnWriteMap;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import jenkins.scm.SCMCheckoutStrategyDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.TokenList;
import org.kohsuke.stapler.export.Exported;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link Job} that allows you to run multiple different configurations
 * from a single setting.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixProject extends AbstractProject<MatrixProject,MatrixBuild> implements TopLevelItem, SCMedItem, ItemGroup<MatrixConfiguration>, Saveable, FlyweightTask, BuildableItemWithBuildWrappers {
    /**
     * Configuration axes.
     */
    private volatile AxisList axes = new AxisList();

    /**
     * The filter that is applied to combinations. It is a Groovy if condition.
     * This can be null, which means "true".
     *
     * @see #getCombinationFilter()
     */
    private volatile String combinationFilter;

    /**
     * List of active {@link Builder}s configured for this project.
     */
    private DescribableList<Builder,Descriptor<Builder>> builders =
            new DescribableList<Builder,Descriptor<Builder>>(this);

    /**
     * List of active {@link Publisher}s configured for this project.
     */
    private DescribableList<Publisher,Descriptor<Publisher>> publishers =
            new DescribableList<Publisher,Descriptor<Publisher>>(this);

    /**
     * List of active {@link BuildWrapper}s configured for this project.
     */
    private DescribableList<BuildWrapper,Descriptor<BuildWrapper>> buildWrappers =
            new DescribableList<BuildWrapper,Descriptor<BuildWrapper>>(this);

    /**
     * All {@link MatrixConfiguration}s, keyed by their {@link MatrixConfiguration#getName() names}.
     */
    private transient /*final*/ Map<Combination,MatrixConfiguration> configurations = new CopyOnWriteMap.Tree<Combination,MatrixConfiguration>();

    /**
     * @see #getActiveConfigurations()
     */
    @CopyOnWrite
    private transient /*final*/ Set<MatrixConfiguration> activeConfigurations = new LinkedHashSet<MatrixConfiguration>();

    /**
     * @deprecated as of 1.456
     *      Moved to {@link DefaultMatrixExecutionStrategyImpl}
     */
    private transient Boolean runSequentially;

    /**
     * Filter to select a number of combinations to build first
     * @deprecated as of 1.456
     *      Moved to {@link DefaultMatrixExecutionStrategyImpl}
     */
    private transient String touchStoneCombinationFilter;

    /**
     * Required result on the touchstone combinations, in order to
     * continue with the rest
     * @deprecated as of 1.456
     *      Moved to {@link DefaultMatrixExecutionStrategyImpl}
     */
    private transient Result touchStoneResultCondition;

    /**
     * @deprecated as of 1.456
     *      Moved to {@link DefaultMatrixExecutionStrategyImpl}
     */
    private transient MatrixConfigurationSorter sorter;

    private MatrixExecutionStrategy executionStrategy;

    /**
     * Custom workspace location for {@link MatrixConfiguration}s.
     *
     * <p>
     * (Historically, we used {@link AbstractProject#customWorkspace} + some unique suffix (see {@link MatrixConfiguration#useShortWorkspaceName})
     * for custom workspace, but we now separated that so that the user has more control.
     *
     * <p>
     * If null, the historical semantics is assumed.
     *
     * @since 1.466
     */
    private String childCustomWorkspace;

    public MatrixProject(String name) {
        this(Jenkins.getInstance(), name);
    }

    public MatrixProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override
    public String getPronoun() {
        return AlternativeUiTextProvider.get(PRONOUN, this, Messages.MatrixProject_Pronoun());
    }

    /**
     * Gets the workspace location that {@link MatrixConfiguration} uses.
     *
     * @see MatrixRun.MatrixRunExecution#decideWorkspace(Node, WorkspaceList)
     *
     * @return never null
     *      even when {@link MatrixProject} uses no custom workspace, this method still
     *      returns something like "${PARENT_WORKSPACE}/${COMBINATION}" that controls
     *      how the workspace should be laid out.
     *
     *      The return value can be absolute or relative. If relative, it is resolved
     *      against the working directory of the overarching {@link MatrixBuild}.
     */
    public String getChildCustomWorkspace() {
        String ws = childCustomWorkspace;
        if (ws==null) {
            ws = MatrixConfiguration.useShortWorkspaceName?"${SHORT_COMBINATION}":"${COMBINATION}";
        }
        return ws;
    }

    /**
     * Do we have an explicit child custom workspace setting (true)? Or just using the default value (false)?
     */
    public boolean hasChildCustomWorkspace() {
        return childCustomWorkspace!=null;
    }

    public void setChildCustomWorkspace(String childCustomWorkspace) throws IOException {
        this.childCustomWorkspace = Util.fixEmptyAndTrim(childCustomWorkspace);
        save();
    }

    /**
     * {@link MatrixProject} is relevant with all the labels its configurations are relevant.
     */
    @Override
    public Set<Label> getRelevantLabels() {
        Set<Label> r = new HashSet<Label>();
        for (MatrixConfiguration c : getActiveConfigurations())
            r.add(c.getAssignedLabel());
        return r;
    }

    /**
     * @return can be null (to indicate that the configurations should be left to their natural order.)
     * @deprecated as of 1.456
     *      Use {@link DefaultMatrixExecutionStrategyImpl#getSorter()}.
     *      This method tries to emulate the previous behavior the best it can, but will return null
     *      if the current {@link MatrixExecutionStrategy} is not the default one.
     */
    public MatrixConfigurationSorter getSorter() {
        MatrixExecutionStrategy e = executionStrategy;
        if (e instanceof DefaultMatrixExecutionStrategyImpl) {
            DefaultMatrixExecutionStrategyImpl dm = (DefaultMatrixExecutionStrategyImpl) e;
            return dm.getSorter();
        }
        return null;
    }

    /**
     * @deprecated as of 1.456
     *      Use {@link DefaultMatrixExecutionStrategyImpl#setSorter(MatrixConfigurationSorter)}.
     *      This method tries to emulate the previous behavior the best it can, but will fall back
     *      to no-op if the current {@link MatrixExecutionStrategy} is not the default one.
     */
    public void setSorter(MatrixConfigurationSorter sorter) throws IOException {
        MatrixExecutionStrategy e = executionStrategy;
        if (e instanceof DefaultMatrixExecutionStrategyImpl) {
            DefaultMatrixExecutionStrategyImpl dm = (DefaultMatrixExecutionStrategyImpl) e;
            dm.setSorter(sorter);
            save();
        }
    }

    public AxisList getAxes() {
        return axes;
    }

    /**
     * Reconfigures axes.
     */
    public void setAxes(AxisList axes) throws IOException {
        this.axes = new AxisList(axes);
        rebuildConfigurations(null);
        save();
    }

    public MatrixExecutionStrategy getExecutionStrategy() {
        return executionStrategy;
    }

    public void setExecutionStrategy(MatrixExecutionStrategy executionStrategy) throws IOException {
        if (executionStrategy ==null)   throw new IllegalArgumentException();
        this.executionStrategy = executionStrategy;
        save();
    }

    /**
     * @deprecated as of 1.456
     *      Use {@link DefaultMatrixExecutionStrategyImpl#isRunSequentially()}.
     *      This method tries to emulate the previous behavior the best it can, but will return false
     *      if the current {@link MatrixExecutionStrategy} is not the default one.
     */
    public boolean isRunSequentially() {
        MatrixExecutionStrategy e = executionStrategy;
        if (e instanceof DefaultMatrixExecutionStrategyImpl) {
            DefaultMatrixExecutionStrategyImpl dm = (DefaultMatrixExecutionStrategyImpl) e;
            return dm.isRunSequentially();
        }
        return false;
    }

    /**
     * @deprecated as of 1.456
     *      Use {@link DefaultMatrixExecutionStrategyImpl#setRunSequentially(boolean)}.
     *      This method tries to emulate the previous behavior the best it can, but will fall back
     *      to no-op if the current {@link MatrixExecutionStrategy} is not the default one.
     */
    public void setRunSequentially(boolean runSequentially) throws IOException {
        MatrixExecutionStrategy e = executionStrategy;
        if (e instanceof DefaultMatrixExecutionStrategyImpl) {
            DefaultMatrixExecutionStrategyImpl dm = (DefaultMatrixExecutionStrategyImpl) e;
            dm.setRunSequentially(runSequentially);
            save();
        }
    }

    /**
     * Sets the combination filter.
     *
     * @param combinationFilter the combinationFilter to set
     */
    public void setCombinationFilter(String combinationFilter) throws IOException {
        this.combinationFilter = combinationFilter;
        rebuildConfigurations(null);
        save();
    }

    /**
     * Obtains the combination filter, used to trim down the size of the matrix.
     *
     * <p>
     * By default, a {@link MatrixConfiguration} is created for every possible combination of axes exhaustively.
     * But by specifying a Groovy expression as a combination filter, one can trim down the # of combinations built.
     *
     * <p>
     * Namely, this expression is evaluated for each axis value combination, and only when it evaluates to true,
     * a corresponding {@link MatrixConfiguration} will be created and built.
     *
     * @return can be null.
     * @since 1.279
     */
    public String getCombinationFilter() {
        return combinationFilter;
    }

    /**
     * @return can be null (to indicate that the configurations should be left to their natural order.)
     * @deprecated as of 1.456
     *      Use {@link DefaultMatrixExecutionStrategyImpl#getTouchStoneCombinationFilter()}.
     *      This method tries to emulate the previous behavior the best it can, but will return null
     *      if the current {@link MatrixExecutionStrategy} is not the default one.
     */
    public String getTouchStoneCombinationFilter() {
        MatrixExecutionStrategy e = executionStrategy;
        if (e instanceof DefaultMatrixExecutionStrategyImpl) {
            DefaultMatrixExecutionStrategyImpl dm = (DefaultMatrixExecutionStrategyImpl) e;
            return dm.getTouchStoneCombinationFilter();
        }
        return null;
    }

    /**
     * @deprecated as of 1.456
     *      Use {@link DefaultMatrixExecutionStrategyImpl#setTouchStoneCombinationFilter(String)}.
     *      This method tries to emulate the previous behavior the best it can, but will fall back
     *      to no-op if the current {@link MatrixExecutionStrategy} is not the default one.
     */
    public void setTouchStoneCombinationFilter(String touchStoneCombinationFilter) throws IOException {
        MatrixExecutionStrategy e = executionStrategy;
        if (e instanceof DefaultMatrixExecutionStrategyImpl) {
            DefaultMatrixExecutionStrategyImpl dm = (DefaultMatrixExecutionStrategyImpl) e;
            dm.setTouchStoneCombinationFilter(touchStoneCombinationFilter);
            save();
        }
    }

    /**
     * @return can be null (to indicate that the configurations should be left to their natural order.)
     * @deprecated as of 1.456
     *      Use {@link DefaultMatrixExecutionStrategyImpl#getTouchStoneResultCondition()}.
     *      This method tries to emulate the previous behavior the best it can, but will return null
     *      if the current {@link MatrixExecutionStrategy} is not the default one.
     */
    public Result getTouchStoneResultCondition() {
        MatrixExecutionStrategy e = executionStrategy;
        if (e instanceof DefaultMatrixExecutionStrategyImpl) {
            DefaultMatrixExecutionStrategyImpl dm = (DefaultMatrixExecutionStrategyImpl) e;
            return dm.getTouchStoneResultCondition();
        }
        return null;
    }

    /**
     * @deprecated as of 1.456
     *      Use {@link DefaultMatrixExecutionStrategyImpl#setTouchStoneResultCondition(Result)}.
     *      This method tries to emulate the previous behavior the best it can, but will fall back
     *      to no-op if the current {@link MatrixExecutionStrategy} is not the default one.
     */
    public void setTouchStoneResultCondition(Result touchStoneResultCondition) throws IOException {
        MatrixExecutionStrategy e = executionStrategy;
        if (e instanceof DefaultMatrixExecutionStrategyImpl) {
            DefaultMatrixExecutionStrategyImpl dm = (DefaultMatrixExecutionStrategyImpl) e;
            dm.setTouchStoneResultCondition(touchStoneResultCondition);
            save();
        }
    }

    @Override
    protected List<Action> createTransientActions() {
        List<Action> r = super.createTransientActions();

        for (BuildStep step : builders)
            r.addAll(step.getProjectActions(this));
        for (BuildStep step : publishers)
            r.addAll(step.getProjectActions(this));
        for (BuildWrapper step : buildWrappers)
            r.addAll(step.getProjectActions(this));
        for (Trigger<?> trigger : triggers())
            r.addAll(trigger.getProjectActions());

        return r;
    }
    
    @Override
    protected void updateTransientActions(){
        super.updateTransientActions();
        if(getActiveConfigurations() !=null){
            // update all transient actions in configurations too.
            for(MatrixConfiguration configuration: getActiveConfigurations()){
                configuration.updateTransientActions();
            }
        }
    }

    /**
     * Gets the subset of {@link AxisList} that are not system axes.
     *
     * @deprecated as of 1.373
     *      System vs user difference are generalized into extension point.
     */
    public List<Axis> getUserAxes() {
        List<Axis> r = new ArrayList<Axis>();
        for (Axis a : axes)
            if(!a.isSystem())
                r.add(a);
        return r;
    }

    public Layouter<MatrixConfiguration> getLayouter() {
        return new Layouter<MatrixConfiguration>(axes) {
            protected MatrixConfiguration getT(Combination c) {
                return getItem(c);
            }
        };
    }

    @Override
    public void onCreatedFromScratch() {
        executionStrategy = new DefaultMatrixExecutionStrategyImpl();
        super.onCreatedFromScratch();
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent,name);
        builders.setOwner(this);
        publishers.setOwner(this);
        buildWrappers.setOwner(this);

        if (executionStrategy ==null)
            executionStrategy = new DefaultMatrixExecutionStrategyImpl(runSequentially != null ? runSequentially : false, touchStoneCombinationFilter, touchStoneResultCondition, sorter);

        rebuildConfigurations(null);
    }

    @Override
    public void logRotate() throws IOException, InterruptedException {
        super.logRotate();
        // perform the log rotation of inactive configurations to make sure
        // their logs get eventually discarded
        for (MatrixConfiguration config : configurations.values()) {
            if(!config.isActiveConfiguration())
                config.logRotate();
        }
    }

    /**
     * Recursively search for configuration and put them to the map
     *
     * <p>
     * The directory structure would be <tt>axis-a/b/axis-c/d/axis-e/f</tt> for
     * combination [a=b,c=d,e=f]. Note that two combinations [a=b,c=d] and [a=b,c=d,e=f]
     * can both co-exist (where one is an archived record and the other is live, for example)
     * so search needs to be thorough.
     *
     * @param dir
     *      Directory to be searched.
     * @param result
     *      Receives the loaded {@link MatrixConfiguration}s.
     * @param combination
     *      Combination of key/values discovered so far while traversing the directories.
     *      Read-only.
     */
    private void loadConfigurations( File dir, CopyOnWriteMap.Tree<Combination,MatrixConfiguration> result, Map<String,String> combination ) {
        File[] axisDirs = dir.listFiles(new FileFilter() {
            public boolean accept(File child) {
                return child.isDirectory() && child.getName().startsWith("axis-");
            }
        });
        if(axisDirs==null)      return;

        for (File subdir : axisDirs) {
            String axis = subdir.getName().substring(5);    // axis name

            File[] valuesDir = subdir.listFiles(new FileFilter() {
                public boolean accept(File child) {
                    return child.isDirectory();
                }
            });
            if(valuesDir==null) continue;   // no values here

            for (File v : valuesDir) {
                Map<String,String> c = new HashMap<String, String>(combination);
                c.put(axis,TokenList.decode(v.getName()));

                try {
                    XmlFile config = Items.getConfigFile(v);
                    if(config.exists()) {
                        Combination comb = new Combination(c);
                        // if we already have this in memory, just use it.
                        // otherwise load it
                        MatrixConfiguration item=null;
                        if(this.configurations!=null)
                            item = this.configurations.get(comb);
                        if(item==null) {
                            item = (MatrixConfiguration) config.read();
                            item.setCombination(comb);
                            item.onLoad(this, v.getName());
                        }
                        result.put(item.getCombination(), item);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to load matrix configuration "+v,e);
                }
                loadConfigurations(v,result,c);
            }
        }
    }

    /**
     * Rebuilds the {@link #configurations} list and {@link #activeConfigurations}.
     *
     * @param context
     *      We rebuild configurations right before a build, to allow configurations to be adjusted for the build.
     *      (think of it as reconfiguring a project right before a build.) And when that happens, this value is the
     *      build in progress. Otherwise this value is null (for example, when Jenkins is booting up.)
     */
    /*package*/ Set<MatrixConfiguration> rebuildConfigurations(MatrixBuildExecution context) throws IOException {
        {
            // backward compatibility check to see if there's any data in the old structure
            // if so, bring them to the newer structure.
            File[] oldDirs = getConfigurationsDir().listFiles(new FileFilter() {
                public boolean accept(File child) {
                    return child.isDirectory() && !child.getName().startsWith("axis-");
                }
            });
            if(oldDirs!=null) {
                // rename the old directory to the new one
                for (File dir : oldDirs) {
                    try {
                        Combination c = Combination.fromString(dir.getName());
                        dir.renameTo(getRootDirFor(c));
                    } catch (IllegalArgumentException e) {
                        // it's not a configuration dir. Just ignore.
                    }
                }
            }
        }

        CopyOnWriteMap.Tree<Combination,MatrixConfiguration> configurations =
            new CopyOnWriteMap.Tree<Combination,MatrixConfiguration>();
        loadConfigurations(getConfigurationsDir(),configurations,Collections.<String,String>emptyMap());
        this.configurations = configurations;

        Iterable<Combination> activeCombinations;
        if (context!=null) {
            List<Set<String>> axesList = Lists.newArrayList();
            for (Axis axis : axes)
                axesList.add(Sets.newLinkedHashSet(axis.rebuild(context)));

            activeCombinations = Iterables.transform(Sets.cartesianProduct(axesList), new Function<List<String>, Combination>() {
                public Combination apply(@Nullable List<String> strings) {
                    assert strings != null;
                    return new Combination(axes, (String[]) strings.toArray(new String[0]));
                }
            });
        } else {
            activeCombinations = axes.list();
        }

        // find all active configurations
        final Set<MatrixConfiguration> active = new LinkedHashSet<MatrixConfiguration>();
        final boolean isDynamicFilter = isDynamicFilter(getCombinationFilter());

        for (Combination c : activeCombinations) {
            if(isDynamicFilter || c.evalGroovyExpression(axes,getCombinationFilter())) {
        		LOGGER.fine("Adding configuration: " + c);
	            MatrixConfiguration config = configurations.get(c);
	            if(config==null) {
	                config = new MatrixConfiguration(this,c);
                    config.onCreatedFromScratch();
	                config.save();
	                configurations.put(config.getCombination(), config);
	            }
	            active.add(config);
        	}
        }
        this.activeConfigurations = active;

        return active;
    }

    private boolean isDynamicFilter(final String filter) {

        if (!isParameterized() || filter == null) return false;

        final ParametersDefinitionProperty paramDefProp = getProperty(ParametersDefinitionProperty.class);

        for (final ParameterDefinition definition : paramDefProp.getParameterDefinitions()) {

            final String name = definition.getName();

            final Matcher matcher = Pattern
                    .compile("\\b" + name + "\\b")
                    .matcher(filter)
            ;

            if (matcher.find()) return true;
        }

        return false;
    }

    private File getConfigurationsDir() {
        return new File(getRootDir(),"configurations");
    }

    /**
     * Gets all active configurations.
     *
     * <p>
     * In contract, inactive configurations are those that are left for archival purpose
     * and no longer built when a new {@link MatrixBuild} is executed.
     *
     * <p>
     * During a build, {@link MatrixBuildExecution#getActiveConfigurations()} should be used
     * to make sure that a build is using the consistent set of active configurations from
     * the start to the end.
     */
    @Exported
    public Collection<MatrixConfiguration> getActiveConfigurations() {
        return activeConfigurations;
    }

    public Collection<MatrixConfiguration> getItems() {
        return configurations.values();
    }

    @Override
    public Collection<? extends Job> getAllJobs() {
        Set<Job> jobs = new HashSet<Job>(getItems());
        jobs.add(this);
        return jobs;
    }

    public String getUrlChildPrefix() {
        return ".";
    }

    public MatrixConfiguration getItem(String name) {
        try {
            return getItem(Combination.fromString(name));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public MatrixConfiguration getItem(Combination c) {
        if (configurations == null) {
            return null;
        }
        return configurations.get(c);
    }

    public File getRootDirFor(MatrixConfiguration child) {
        return getRootDirFor(child.getCombination());
    }

    public void onRenamed(MatrixConfiguration item, String oldName, String newName) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void onDeleted(MatrixConfiguration item) throws IOException {
        // noop
    }

    public File getRootDirFor(Combination combination) {
        File f = getConfigurationsDir();
        for (Entry<String, String> e : combination.entrySet())
            f = new File(f,"axis-"+e.getKey()+'/'+Util.rawEncode(e.getValue()));
        f.getParentFile().mkdirs();
        return f;
    }

    /**
     * @see #getJDKs()
     */
    @Override @Deprecated
    public JDK getJDK() {
        return super.getJDK();
    }

    /**
     * Gets the {@link JDK}s where the builds will be run.
     * @return never null but can be empty
     */
    public Set<JDK> getJDKs() {
        Axis a = axes.find("jdk");
        if(a==null)  return Collections.emptySet();
        Set<JDK> r = new HashSet<JDK>();
        for (String j : a) {
            JDK jdk = Jenkins.getInstance().getJDK(j);
            if(jdk!=null)
                r.add(jdk);
        }
        return r;
    }

    /**
     * Gets the {@link Label}s where the builds will be run.
     * @return never null
     */
    public Set<Label> getLabels() {
        Set<Label> r = new HashSet<Label>();
        for (Combination c : axes.subList(LabelAxis.class).list())
            r.add(Jenkins.getInstance().getLabel(Util.join(c.values(),"&&")));
        return r;
    }

    public List<Builder> getBuilders() {
        return builders.toList();
    }

    public DescribableList<Builder,Descriptor<Builder>> getBuildersList() {
        return builders;
    }

    public Map<Descriptor<Publisher>,Publisher> getPublishers() {
        return publishers.toMap();
    }

    public DescribableList<Publisher,Descriptor<Publisher>> getPublishersList() {
        return publishers;
    }

    public DescribableList<BuildWrapper, Descriptor<BuildWrapper>> getBuildWrappersList() {
        return buildWrappers;
    }

    public Map<Descriptor<BuildWrapper>,BuildWrapper> getBuildWrappers() {
        return buildWrappers.toMap();
    }

    public Publisher getPublisher(Descriptor<Publisher> descriptor) {
        for (Publisher p : publishers) {
            if(p.getDescriptor()==descriptor)
                return p;
        }
        return null;
    }

    protected Class<MatrixBuild> getBuildClass() {
        return MatrixBuild.class;
    }

    public boolean isFingerprintConfigured() {
        return false;
    }

    protected void buildDependencyGraph(DependencyGraph graph) {
        publishers.buildDependencyGraph(this,graph);
        builders.buildDependencyGraph(this,graph);
        buildWrappers.buildDependencyGraph(this,graph);
    }

    public MatrixProject asProject() {
        return this;
    }

    @Override
    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        try {
            MatrixConfiguration item = getItem(token);
            if(item!=null)
            return item;
        } catch (IllegalArgumentException _) {
            // failed to parse the token as Combination. Must be something else
        }
        return super.getDynamic(token,req,rsp);
    }

    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        super.submit(req, rsp);

        JSONObject json = req.getSubmittedForm();

        if(req.getParameter("hasCombinationFilter")!=null) {
            this.combinationFilter = Util.nullify(req.getParameter("combinationFilter"));
        } else {
            this.combinationFilter = null;
        }

        if(req.hasParameter("customWorkspace")) {
            setChildCustomWorkspace(Util.fixEmptyAndTrim(req.getParameter("childCustomWorkspace.directory")));
        } else {
            setChildCustomWorkspace(null);
        }

        List<MatrixExecutionStrategyDescriptor> esd = getDescriptor().getExecutionStrategyDescriptors();
        if (esd.size()>1)
            executionStrategy = req.bindJSON(MatrixExecutionStrategy.class,json.getJSONObject("executionStrategy"));
        else
            executionStrategy = req.bindJSON(esd.get(0).clazz,json.getJSONObject("executionStrategy"));

        // parse system axes
        DescribableList<Axis,AxisDescriptor> newAxes = new DescribableList<Axis,AxisDescriptor>(this);
        newAxes.rebuildHetero(req, json, Axis.all(),"axis");
        checkAxisNames(newAxes);
        this.axes = new AxisList(newAxes.toList());

        buildWrappers.rebuild(req, json, BuildWrappers.getFor(this));
        builders.rebuildHetero(req, json, Builder.all(), "builder");
        publishers.rebuildHetero(req, json, Publisher.all(), "publisher");

        rebuildConfigurations(null);
    }

    /**
     * Verifies that Axis names are valid and unique.
     */
    private void checkAxisNames(Iterable<Axis> newAxes) throws FormException {
        HashSet<String> axisNames = new HashSet<String>();
        for (Axis a : newAxes) {
            FormValidation fv = a.getDescriptor().doCheckName(a.getName());
            if (fv.kind!=Kind.OK)
                throw new FormException(Messages.MatrixProject_DuplicateAxisName(),fv,"axis.name");

            if (axisNames.contains(a.getName()))
                throw new FormException(Messages.MatrixProject_DuplicateAxisName(),"axis.name");
            axisNames.add(a.getName());
        }
    }

    /**
     * Also delete all the workspaces of the configuration, too.
     */
    @Override
    public HttpResponse doDoWipeOutWorkspace() throws IOException, ServletException, InterruptedException {
        HttpResponse rsp = super.doDoWipeOutWorkspace();
        for (MatrixConfiguration c : configurations.values())
            c.doDoWipeOutWorkspace();
        return rsp;
    }

    @Override
    public ContextMenu doChildrenContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        ContextMenu menu = new ContextMenu();
        for (MatrixConfiguration c : getActiveConfigurations()) {
            menu.add(c);
        }
        return menu;
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Descriptor is instantiated as a field purely for backward compatibility.
     * Do not do this in your code. Put @Extension on your DescriptorImpl class instead.
     */
    @Restricted(NoExternalUse.class)
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends AbstractProjectDescriptor {
        public String getDisplayName() {
            return Messages.MatrixProject_DisplayName();
        }

        public MatrixProject newInstance(ItemGroup parent, String name) {
            return new MatrixProject(parent,name);
        }

        /**
         * All {@link AxisDescriptor}s that contribute to the UI.
         */
        public List<AxisDescriptor> getAxisDescriptors() {
            List<AxisDescriptor> r = new ArrayList<AxisDescriptor>();
            for (AxisDescriptor d : Axis.all()) {
                if (d.isInstantiable())
                    r.add(d);
            }
            return r;
        }

        /**
         * @deprecated as of 1.456
         *      This was only exposed for Jelly.
         */
        public List<MatrixConfigurationSorterDescriptor> getSorterDescriptors() {
            return MatrixConfigurationSorterDescriptor.all();
        }

        public List<MatrixExecutionStrategyDescriptor> getExecutionStrategyDescriptors() {
            return MatrixExecutionStrategyDescriptor.all();
        }

        public List<SCMCheckoutStrategyDescriptor> getMatrixRunCheckoutStrategyDescriptors() {
            return SCMCheckoutStrategyDescriptor.all();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(MatrixProject.class.getName());
}
