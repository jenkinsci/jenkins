package hudson.scm;

import hudson.model.AbstractBuild;
import hudson.model.TaskAction;
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
public abstract class AbstractScmTagAction extends TaskAction {
    protected final AbstractBuild build;

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

    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        req.getView(this,chooseAction()).forward(req,rsp);
    }

    protected synchronized String chooseAction() {
        if(workerThread!=null)
            return "inProgress.jelly";
        return "form.jelly";
    }

}
