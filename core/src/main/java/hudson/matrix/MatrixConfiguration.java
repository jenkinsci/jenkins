package hudson.matrix;

import hudson.FilePath;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.JDK;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Project;
import hudson.model.SCMedItem;
import hudson.scm.SCM;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.LogRotator;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.util.Map;

/**
 * One configuration of {@link MatrixProject}.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixConfiguration extends Project<MatrixConfiguration,MatrixRun> implements SCMedItem {
    /**
     * The actual value combination.
     */
    private transient /*final*/ Combination combination;

    public MatrixConfiguration(MatrixProject parent, Combination c) {
        super(parent,c.toString());
        this.combination = c;
    }

    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        // directory name is not a name for us --- it's taken from the combination name
        super.onLoad(parent, combination.toString());
    }

    /**
     * Used during loading to set the combination back.
     */
    /*package*/ void setCombination(Combination c) {
        this.combination = c;
    }

    /**
     * Build numbers are always synchronized with the parent.
     */
    @Override
    public int getNextBuildNumber() {
        MatrixBuild lb = getParent().getLastBuild();
        return lb!=null ? lb.getNumber() : 0;
    }

    public int assignBuildNumber() throws IOException {
        int nb = getNextBuildNumber();
        MatrixRun r = getLastBuild();
        if(r!=null && r.getNumber()>=nb) // make sure we don't schedule the same build twice
            throw new IllegalStateException("Build #"+nb+" is already completed");
        return nb;
    }

    @Override
    public String getDisplayName() {
        return combination.toCompactString(getParent().getAxes());
    }

    public MatrixProject getParent() {
        return (MatrixProject)super.getParent();
    }

    /**
     * Get the actual combination of the axes values for this {@link MatrixConfiguration}
     */
    public Combination getCombination() {
        return combination;
    }

    @Override
    public FilePath getWorkspace() {
        Node node = getLastBuiltOn();
        if(node==null)  node = Hudson.getInstance();
        return node.getWorkspaceFor(getParent()).child(getName());
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    protected Class<MatrixRun> getBuildClass() {
        return MatrixRun.class;
    }

    @Override
    protected MatrixRun newBuild() throws IOException {
        // for every MatrixRun there should be a parent MatrixBuild
        MatrixBuild lb = getParent().getLastBuild();
        MatrixRun lastBuild = new MatrixRun(this, lb.getTimestamp());
        lastBuild.number = lb.getNumber();

        builds.put(lastBuild);
        return lastBuild;
    }

    @Override
    public boolean isFingerprintConfigured() {
        // TODO
        return false;
    }

    @Override
    protected void buildDependencyGraph(DependencyGraph graph) {
    }

    public MatrixConfiguration asProject() {
        return this;
    }

    @Override
    public Label getAssignedLabel() {
        return Hudson.getInstance().getLabel(combination.get("label"));
    }

    @Override
    public String getPronoun() {
        return "Configuration";
    }

    @Override
    public JDK getJDK() {
        return Hudson.getInstance().getJDK(combination.get("jdk"));
    }

//
// inherit build setting from the parent project
//
    @Override
    public Map<Descriptor<Builder>, Builder> getBuilders() {
        return getParent().getBuilders();
    }

    @Override
    public Map<Descriptor<Publisher>, Publisher> getPublishers() {
        return getParent().getPublishers();
    }

    @Override
    public Map<Descriptor<BuildWrapper>, BuildWrapper> getBuildWrappers() {
        return getParent().getBuildWrappers();
    }

    @Override
    public Publisher getPublisher(Descriptor<Publisher> descriptor) {
        return getParent().getPublisher(descriptor);
    }

    @Override
    public LogRotator getLogRotator() {
        return new LinkedLogRotator();
    }

    @Override
    public SCM getScm() {
        return getParent().getScm();
    }
    
    /**
     * JDK cannot be set on {@link MatrixConfiguration} because
     * it's controlled by {@link MatrixProject}.
     * @deprecated
     *      Not supported.
     */
    public void setJDK(JDK jdk) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     *      Value is controlled by {@link MatrixProject}.
     */
    public void setLogRotator(LogRotator logRotator) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true if this configuration is a configuration
     * currently in use today (as opposed to the ones that are
     * there only to keep the past record.) 
     *
     * @see MatrixProject#getActiveConfigurations()
     */
    public boolean isActiveConfiguration() {
        return getParent().getActiveConfigurations().contains(this);
    }
}
