package hudson.matrix;

import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ItemGroupMixIn;
import static hudson.model.ItemGroupMixIn.KEYED_BY_NAME;
import hudson.model.JDK;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.SCMedItem;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.util.CopyOnWriteMap;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Kohsuke Kawaguchi
 */
public class MatrixProject extends AbstractProject<MatrixProject,MatrixBuild> implements TopLevelItem, SCMedItem, ItemGroup<MatrixConfiguration> {
    /**
     * Other configuration axes.
     *
     * This also includes special axis "label" and "jdk" if they are configured.
     */
    private volatile AxisList axes = new AxisList();

    /**
     * All {@link MatrixConfiguration}s, keyed by their {@link MatrixConfiguration#getName() names}.
     */
    private transient /*final*/ Map<String,MatrixConfiguration> configurations = new CopyOnWriteMap.Tree<String,MatrixConfiguration>();

    /**
     * @see #getActiveConfigurations()
     */
    private transient /*final*/ Set<MatrixConfiguration> activeConfigurations = new LinkedHashSet<MatrixConfiguration>();

    public MatrixProject(String name) {
        super(Hudson.getInstance(), name);
    }

    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent,name);
        Collections.sort(axes); // perhaps the file was edited on disk and the sort order might have been broken

        configurations = ItemGroupMixIn.<String,MatrixConfiguration>loadChildren(this,getConfigurationsDir(), KEYED_BY_NAME);

        // find all active configurations
        Set<MatrixConfiguration> active = new LinkedHashSet<MatrixConfiguration>();
        for (Combination c : axes.list()) {
            MatrixConfiguration config = configurations.get(c.toString());
            if(config==null) {
                config = new MatrixConfiguration(this,c);
                configurations.put(config.getName(), config);
            }
            active.add(config);
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

    public String getUrlChildPrefix() {
        return ".";
    }

    public MatrixConfiguration getItem(String name) {
        return configurations.get(name);
    }

    public File getRootDirFor(MatrixConfiguration child) {
        return new File(getConfigurationsDir(),child.getName());
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
        // TODO: perhaps support downstream build triggering
    }

    public MatrixProject asProject() {
        return this;
    }


    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        super.submit(req, rsp);

        AxisList newAxes = new AxisList();
        newAxes.add(Axis.parsePrefixed(req,"jdk"));
        newAxes.add(Axis.parsePrefixed(req,"label"));
        this.axes = newAxes;
    }

    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends TopLevelItemDescriptor {
        private DescriptorImpl() {
            super(MatrixProject.class);
        }

        public String getDisplayName() {
            return "Build multi-configuration project";
        }

        public MatrixProject newInstance(String name) {
            return new MatrixProject(name);
        }
    }
}
