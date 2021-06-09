package hudson.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;

public abstract class WorkspaceListener implements ExtensionPoint {
    
    /**
     * Called after a workspace is deleted successfully.
     * @param project the job that build software
     */
    public void afterDelete(AbstractProject project) {
        
    }

    /**
     * Called before a build uses a workspace. IE, before any SCM checkout.
     * @param b the build run
     * @param workspace the workspace file path
     * @param listener the build listerner
     */
    public void beforeUse(AbstractBuild b, FilePath workspace, BuildListener listener) {
        
    }
    
    /**
     * All registered {@link WorkspaceListener}s.
     */
    public static ExtensionList<WorkspaceListener> all() {
        return ExtensionList.lookup(WorkspaceListener.class);
    }

}
