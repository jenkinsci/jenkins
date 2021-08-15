package hudson.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;

public abstract class WorkspaceListener implements ExtensionPoint {
    
    /**
     * Called after a workspace is deleted successfully.
     */
    public void afterDelete(AbstractProject project) {
        
    }

    /**
     * Called before a build uses a workspace. IE, before any SCM checkout.
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
