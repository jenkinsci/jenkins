package hudson.scm;

import hudson.model.AbstractBuild;
import hudson.model.TaskAction;
import hudson.model.BuildBadgeAction;
import hudson.security.Permission;
import hudson.security.ACL;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

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
public abstract class AbstractScmTagAction extends TaskAction implements BuildBadgeAction {
    protected final AbstractBuild build;

    protected AbstractScmTagAction(AbstractBuild build) {
        this.build = build;
    }

    public final String getUrlName() {
        // to make this consistent with CVSSCM, even though the name is bit off
        return "tagBuild";
    }

    /**
     * Defaults to {@link SCM#TAG}.
     */
    protected Permission getPermission() {
        return SCM.TAG;
    }

    public AbstractBuild getBuild() {
        return build;
    }

    /**
     * This message is shown as the tool tip of the build badge icon.
     */
    public String getTooltip() {
        return null;
    }

    /**
     * Returns true if the build is tagged already.
     */
    public abstract boolean isTagged();

    protected ACL getACL() {
        return build.getACL();
    }

    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        req.getView(this,chooseAction()).forward(req,rsp);
    }

    protected synchronized String chooseAction() {
        if(workerThread!=null)
            return "inProgress.jelly";
        return "tagForm.jelly";
    }

}
