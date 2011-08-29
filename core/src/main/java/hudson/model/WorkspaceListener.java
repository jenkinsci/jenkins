package hudson.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;

public abstract class WorkspaceListener implements ExtensionPoint {
    
    /**
     * Called after a workspace is deleted successfully.
     * @param project
     */
    public void afterDelete(AbstractProject project) {
        
    }
    
    /**
     * All registered {@link WorkspaceListener}s.
     */
    public static ExtensionList<WorkspaceListener> all() {
        return Hudson.getInstance().getExtensionList(WorkspaceListener.class);
    }

}
