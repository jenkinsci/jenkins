package hudson.model;

import hudson.FilePath;

/**
 * Free-style software project.
 * 
 * @author Kohsuke Kawaguchi
 */
public class FreeStyleProject extends Project<FreeStyleProject,FreeStyleBuild> implements TopLevelItem {

    public FreeStyleProject(Hudson parent, String name) {
        super(parent, name);
    }

    @Override
    protected Class<FreeStyleBuild> getBuildClass() {
        return FreeStyleBuild.class;
    }

    @Override
    public Hudson getParent() {
        return Hudson.getInstance();
    }

    @Override
    public FilePath getWorkspace() {
        Node node = getLastBuiltOn();
        if(node==null)  node = getParent();
        return node.getWorkspaceFor(this);
    }

    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends TopLevelItemDescriptor {
        private DescriptorImpl() {
            super(FreeStyleProject.class);
        }

        public String getDisplayName() {
            return "Build a free-style software project";
        }

        public FreeStyleProject newInstance(String name) {
            return new FreeStyleProject(Hudson.getInstance(),name);
        }
    }
}
