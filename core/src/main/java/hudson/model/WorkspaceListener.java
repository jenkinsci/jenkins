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
     *      The ${@link AbstractProject} this workspace was created for
     * @deprecated as of TODO_BEFORE_MERGE Use {@link #afterDelete(Job)}.
     */
    @Deprecated public void afterDelete(AbstractProject project) {
        if (Util.isOverridden(WorkspaceListener.class, getClass(), "afterDelete", Job.class)) {
            // old client calling newer implementation. forward the call.
            afterDelete((Job) project);
        }
    }

    /**
     * Called after a workspace is deleted successfully.
     * @param job
     *      The ${@link Job} this workspace was created for
     * @since TODO_BEFORE_MERGE
     */
    public void afterDelete(Job job) {
        // this implementation is backward compatibility thunk in case subtypes only override the
        // old signature (AbstractProject)

        if (job instanceof AbstractProject) {
            // maybe the plugin implements the old signature.
            afterDelete((AbstractProject) job);
        }
    }

    /**
     * Called before a build uses a workspace. IE, before any SCM checkout.
     * @param b
     *      The ${@link AbstractBuild} to use this workspace
     * @param workspace
     *      The workspace being acquired by r
     * @param listener
     *      Run's build listener
     * @deprecated as of TODO_BEFORE_MERGE Use {@link #beforeUse(Run, FilePath, BuildListener)}.
     */
    @Deprecated public void beforeUse(AbstractBuild b, FilePath workspace, BuildListener listener) {
        if (Util.isOverridden(WorkspaceListener.class, getClass(), "beforeUse", Run.class, FilePath.class, BuildListener.class)) {
            // old client calling newer implementation. forward the call.
            beforeUse((Run) b, workspace, listener);
        }
    }

    /**
     * Called before a build uses a workspace. IE, before any SCM checkout.
     * @param r
     *      The ${@link Run} to use this workspace
     * @param workspace
     *      The workspace being acquired by r
     * @param listener
     *      Run's build listener
     * @since TODO_BEFORE_MERGE
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
