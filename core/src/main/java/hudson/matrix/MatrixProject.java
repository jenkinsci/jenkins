/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jorg Heymans, Red Hat, Inc., id:cactusman
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

import hudson.CopyOnWrite;
import hudson.FilePath;
import hudson.XmlFile;
import hudson.Util;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.JDK;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.SCMedItem;
import hudson.model.Saveable;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.Descriptor.FormException;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;
import hudson.util.CopyOnWriteMap;
import hudson.util.DescribableList;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * {@link Job} that allows you to run multiple different configurations
 * from a single setting.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixProject extends AbstractProject<MatrixProject,MatrixBuild> implements TopLevelItem, SCMedItem, ItemGroup<MatrixConfiguration>, Saveable {
    /**
     * Other configuration axes.
     *
     * This also includes special axis "label" and "jdk" if they are configured.
     */
    private volatile AxisList axes = new AxisList();
    
    /**
     * The filter that is applied to combinatios. It is a Groovy if condition.
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

    public MatrixProject(String name) {
        super(Hudson.getInstance(), name);
    }

    public AxisList getAxes() {
        return axes;
    }

    /**
     * Reconfigures axes.
     */
    public void setAxes(AxisList axes) throws IOException {
        this.axes = new AxisList(axes);
        rebuildConfigurations();
        save();
    }

    /**
     * Sets the combination filter.
     *
	 * @param combinationFilter the combinationFilter to set
	 */
	public void setCombinationFilter(String combinationFilter) throws IOException {
		this.combinationFilter = combinationFilter;
		rebuildConfigurations();
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

	protected void updateTransientActions() {
        synchronized(transientActions) {
            super.updateTransientActions();

            for (BuildStep step : builders) {
                Action a = step.getProjectAction(this);
                if(a!=null)
                    transientActions.add(a);
            }
            for (BuildStep step : publishers) {
                Action a = step.getProjectAction(this);
                if(a!=null)
                    transientActions.add(a);
            }
            for (BuildWrapper step : buildWrappers) {
                Action a = step.getProjectAction(this);
                if(a!=null)
                    transientActions.add(a);
            }
            for (Trigger trigger : triggers) {
                Action a = trigger.getProjectAction();
                if(a!=null)
                    transientActions.add(a);
            }
        }
    }

    /**
     * Gets the subset of {@link AxisList} that are not system axes.
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

    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent,name);
        Collections.sort(axes); // perhaps the file was edited on disk and the sort order might have been broken
        builders.setOwner(this);
        publishers.setOwner(this);
        buildWrappers.setOwner(this);
        rebuildConfigurations();
    }

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
                c.put(axis,v.getName());

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
     */
    private void rebuildConfigurations() throws IOException {
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

        // find all active configurations
        Set<MatrixConfiguration> active = new LinkedHashSet<MatrixConfiguration>();
        for (Combination c : axes.list()) {
            if(c.evalGroovyExpression(axes,combinationFilter)) {
        		LOGGER.fine("Adding configuration: " + c);
	            MatrixConfiguration config = configurations.get(c);
	            if(config==null) {
	                config = new MatrixConfiguration(this,c);
	                config.save();
	                configurations.put(config.getCombination(), config);
	            }
	            active.add(config);
        	}
        }
        this.activeConfigurations = active;
    }

    private File getConfigurationsDir() {
        return new File(getRootDir(),"configurations");
    }

    /**
     * Gets all active configurations.
     * <p>
     * In contract, inactive configurations are those that are left for archival purpose
     * and no longer built when a new {@link MatrixBuild} is executed.
     */
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
        return getItem(Combination.fromString(name));
    }

    public MatrixConfiguration getItem(Combination c) {
        return configurations.get(c);
    }

    public File getRootDirFor(MatrixConfiguration child) {
        return getRootDirFor(child.getCombination());
    }

    public File getRootDirFor(Combination combination) {
        File f = getConfigurationsDir();
        for (Entry<String, String> e : combination.entrySet())
            f = new File(f,"axis-"+e.getKey()+'/'+e.getValue());
        f.getParentFile().mkdirs();
        return f;
    }

    public Hudson getParent() {
        return Hudson.getInstance();
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
            JDK jdk = Hudson.getInstance().getJDK(j);
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
        Axis a = axes.find("label");
        if(a==null) return Collections.emptySet();

        Set<Label> r = new HashSet<Label>();
        for (String l : a)
            r.add(Hudson.getInstance().getLabel(l));
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

    @Override
    public FilePath getWorkspace() {
        Node node = getLastBuiltOn();
        if(node==null)  node = getParent();
        return node.getWorkspaceFor(this);
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

    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        super.submit(req, rsp);

        AxisList newAxes = new AxisList();

        // parse user axes
        if(req.getParameter("hasAxes")!=null) {
            newAxes.addAll(req.bindParametersToList(Axis.class,"axis."));
            // get rid of empty values
            for (Iterator<Axis> itr = newAxes.iterator(); itr.hasNext();) {
                Axis a = itr.next();
                if(a.values.isEmpty())  itr.remove();
            }
        }
        
        if(req.getParameter("hasCombinationFilter")!=null)
            this.combinationFilter = Util.nullify(req.getParameter("combinationFilter"));
        else
            this.combinationFilter = null;

        // parse system axes
        newAxes.add(Axis.parsePrefixed(req,"jdk"));
        if(req.getParameter("multipleNodes")!=null)
            newAxes.add(Axis.parsePrefixed(req,"label"));
        this.axes = newAxes;

        JSONObject json = req.getSubmittedForm();

        buildWrappers.rebuild(req, json, BuildWrappers.getFor(this));
        builders.rebuildHetero(req, json, Builder.all(), "builder");
        publishers.rebuild(req, json, BuildStepDescriptor.filter(Publisher.all(),this.getClass()));
        updateTransientActions(); // to pick up transient actions from builder, publisher, etc.

        rebuildConfigurations();
    }

    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends AbstractProjectDescriptor {
        public String getDisplayName() {
            return Messages.MatrixProject_DisplayName();
        }

        public MatrixProject newInstance(String name) {
            return new MatrixProject(name);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(MatrixProject.class.getName());
}
