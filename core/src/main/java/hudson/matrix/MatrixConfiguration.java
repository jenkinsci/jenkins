package hudson.matrix;

import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.DependencyGraph;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.SCMedItem;
import hudson.model.JDK;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Label;

import java.io.IOException;

/**
 * One configuration of {@link MatrixProject}.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixConfiguration extends AbstractProject<MatrixConfiguration,MatrixRun> implements SCMedItem {
    /**
     * The actual value combination.
     */
    private transient /*final*/ Combination combination;

    public MatrixConfiguration(MatrixProject parent, Combination c) {
        super(parent,c.toString());
        this.combination = c;
    }

    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        combination = Combination.fromString(name);
    }

    public MatrixProject getParent() {
        return (MatrixProject)super.getParent();
    }

    @Override
    public FilePath getWorkspace() {
        Node node = getLastBuiltOn();
        if(node==null)  node = Hudson.getInstance();
        return node.getWorkspaceFor(getParent()).child(getName());
    }

    @Override
    protected Class<MatrixRun> getBuildClass() {
        return MatrixRun.class;
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
    public JDK getJDK() {
        return Hudson.getInstance().getJDK(combination.get("jdk"));
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
}
