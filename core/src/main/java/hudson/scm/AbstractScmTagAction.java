package hudson.scm;

import hudson.model.LargeText;
import hudson.model.TaskListener;
import hudson.model.AbstractModelObject;
import hudson.model.Action;
import hudson.model.AbstractBuild;
import hudson.model.Hudson;
import hudson.util.ByteBuffer;
import hudson.util.StreamTaskListener;

import java.lang.ref.WeakReference;
import java.io.IOException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

/**
 * Common part of {@link CVSSCM.TagAction} and {@link SubversionTagAction}.
 *
 * <p>
 * This class implements the action that tags the modules. Derived classes
 * need to provide <tt>tagForm.jelly</tt> view that displays a form for
 * letting user start tagging.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractScmTagAction extends AbstractModelObject implements Action {
    protected final AbstractBuild build;

    /**
     * If non-null, that means the tagging is in progress
     * (asynchronously.)
     */
    protected transient volatile AbstractTagWorkerThread workerThread;

    /**
     * Hold the log of the tagging operation.
     */
    protected transient WeakReference<LargeText> log;

    protected AbstractScmTagAction(AbstractBuild build) {
        this.build = build;
    }

    public final String getUrlName() {
        // to make this consistent with CVSSCM, even though the name is bit off
        return "tagBuild";
    }

    public String getSearchUrl() {
        return getUrlName();
    }

    public AbstractBuild getBuild() {
        return build;
    }

    public AbstractTagWorkerThread getWorkerThread() {
        return workerThread;
    }

    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        req.setAttribute("build",build);
        req.getView(this,chooseAction()).forward(req,rsp);
    }

    private synchronized String chooseAction() {
        if(workerThread!=null)
            return "inProgress.jelly";
        return "tagForm.jelly";
    }

    /**
     * Handles incremental log output.
     */
    public void doProgressiveLog( StaplerRequest req, StaplerResponse rsp) throws IOException {
        if(log==null) {
            rsp.setStatus(HttpServletResponse.SC_OK);
        } else {
            LargeText text = log.get();
            if(text!=null)
                text.doProgressText(req,rsp);
            else
                rsp.setStatus(HttpServletResponse.SC_OK);
        }
    }

    /**
     * Clears the error status.
     */
    public synchronized void doClearError(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        if(workerThread!=null && !workerThread.isAlive())
            workerThread = null;
        rsp.sendRedirect(".");
    }


    public static abstract class AbstractTagWorkerThread extends Thread {
        // StringWriter is synchronized
        protected final ByteBuffer log = new ByteBuffer();
        protected final LargeText text = new LargeText(log,false);

        public String getLog() {
            // this method can be invoked from another thread.
            return log.toString();
        }

        public final void run() {
            TaskListener listener = new StreamTaskListener(log);
            perform(listener);
            listener.getLogger().println("Completed");
            text.markAsComplete();
        }

        /**
         * Do the actual work.
         */
        protected abstract void perform(TaskListener listener);
    }
}
