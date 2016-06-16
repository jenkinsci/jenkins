package hudson.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Util;
import hudson.console.ConsoleLogFilter;

import java.io.OutputStream;

public abstract class WorkspaceListener implements ExtensionPoint {
    
    /**
     * Called after a workspace is deleted successfully.
     * @param project
     */
    public void afterDelete(AbstractProject project) {
        
    }

    /**
     * Called before a build uses a workspace. IE, before any SCM checkout.
     * @param b
     * @param workspace
     * @param listener
     * @deprecated as of TODO_BEFORE_MERGE Use {@link #beforeUse(Run, FilePath, BuildListener)}.
     */
    public void beforeUse(AbstractBuild b, FilePath workspace, BuildListener listener) {
        if (Util.isOverridden(WorkspaceListener.class, getClass(), "beforeUse", Run.class, FilePath.class, BuildListener.class)) {
            // old client calling newer implementation. forward the call.
            beforeUse((Run) b, workspace, listener);
        } else {
            // happens only if the subtype fails to override neither beforeUse method
            throw new AssertionError("The plugin '" + this.getClass().getName() + "' still uses " +
                    "deprecated beforeUse(AbstractBuild,FilePath,BuildListener) method. " +
                    "Update the plugin to use beforeUse(Run,FilePath,BuildListener) instead.");
        }
    }

    /**
     * Called before a build uses a workspace. IE, before any SCM checkout.
     * @param r
     * @param workspace
     * @param listener
     */
    public void beforeUse(Run r, FilePath workspace, BuildListener listener) {
        // this implementation is backward compatibility thunk in case subtypes only override the
        // old signature (AbstractBuild,FilePath,BuildListener)

        if (r instanceof AbstractBuild) {
            // maybe the plugin implements the old signature.
            beforeUse((AbstractBuild) r, workspace, listener);
        }
    }
    
    /**
     * All registered {@link WorkspaceListener}s.
     */
    public static ExtensionList<WorkspaceListener> all() {
        return ExtensionList.lookup(WorkspaceListener.class);
    }

}
