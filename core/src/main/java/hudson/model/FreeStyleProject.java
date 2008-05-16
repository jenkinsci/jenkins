package hudson.model;

import hudson.FilePath;

import java.io.File;
import java.io.IOException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;

/**
 * Free-style software project.
 * 
 * @author Kohsuke Kawaguchi
 */
public class FreeStyleProject extends Project<FreeStyleProject,FreeStyleBuild> implements TopLevelItem {
    /**
     * User-specified workspace directory, or null if it's up to Hudson.
     *
     * <p>
     * Normally a free-style project uses the workspace location assigned by its parent container,
     * but sometimes people have builds that have hard-coded paths (which can be only built in
     * certain locations. see http://www.nabble.com/Customize-Workspace-directory-tt17194310.html for
     * one such discussion.)
     *
     * <p>
     * This is not {@link File} because it may have to hold a path representation on another OS.
     *
     * @since 1.216
     */
    private String customWorkspace;

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

    public String getCustomWorkspace() {
        return customWorkspace;
    }

    @Override
    public FilePath getWorkspace() {
        Node node = getLastBuiltOn();
        if(node==null)  node = getParent();
        if(customWorkspace!=null)
            return node.createPath(customWorkspace);
        return node.getWorkspaceFor(this);
    }

    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        if(req.hasParameter("customWorkspace"))
            customWorkspace = req.getParameter("customWorkspace.directory");
        else
            customWorkspace = null;

        super.submit(req, rsp);
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
            return Messages.FreeStyleProject_DisplayName();
        }

        public FreeStyleProject newInstance(String name) {
            return new FreeStyleProject(Hudson.getInstance(),name);
        }
    }
}
