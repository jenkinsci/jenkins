package hudson.scm;

import hudson.model.LargeText;
import hudson.model.TaskListener;
import hudson.model.AbstractModelObject;
import hudson.model.Action;
import hudson.model.AbstractBuild;
import hudson.util.ByteBuffer;
import hudson.util.StreamTaskListener;

import java.lang.ref.WeakReference;
import java.io.IOException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.http.HttpServletResponse;

/**
 * Common part of {@link CVSSCM.TagAction} and {@link SubversionTagAction}.
 * @author Kohsuke Kawaguchi
 */
abstract class AbstractScmTagAction extends AbstractModelObject implements Action {
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

    public AbstractBuild getBuild() {
        return build;
    }

    public AbstractTagWorkerThread getWorkerThread() {
        return workerThread;
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
