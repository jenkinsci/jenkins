package hudson.util;

import hudson.Util;
import hudson.FilePath;
import static hudson.Util.fixEmpty;
import hudson.model.Hudson;
import hudson.model.AbstractProject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class FormFieldValidator {
    protected final StaplerRequest request;
    protected final StaplerResponse response;
    private final boolean isAdminOnly;

    protected FormFieldValidator(StaplerRequest request, StaplerResponse response, boolean adminOnly) {
        this.request = request;
        this.response = response;
        isAdminOnly = adminOnly;
    }

    /**
     * Runs the validation code.
     */
    public final void process() throws IOException, ServletException {
        if(isAdminOnly && !Hudson.adminCheck(request,response))
            return; // failed check

        check();
    }

    protected abstract void check() throws IOException, ServletException;

    /**
     * Gets the parameter as a file.
     */
    protected final File getFileParameter(String paramName) {
        return new File(Util.fixNull(request.getParameter(paramName)));
    }

    /**
     * Sends out an HTML fragment that indicates a success.
     */
    public void ok() throws IOException, ServletException {
        response.setContentType("text/html");
        response.getWriter().print("<div/>");
    }

    /**
     * Sends out an HTML fragment that indicates an error.
     *
     * @param message
     *      Human readable message to be sent. <tt>error(null)</tt>
     *      can be used as <tt>ok()</tt>.
     */
    public void error(String message) throws IOException, ServletException {
        if(message==null) {
            ok();
        } else {
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().print("<div class=error>"+message+"</div>");
        }
    }

    /**
     * Checks the file mask (specified in the 'value' query parameter) against
     * the current workspace.
     * @since 1.90.
     */
    public static class WorkspaceFileMask extends FormFieldValidator {

        public WorkspaceFileMask(StaplerRequest request, StaplerResponse response) {
            super(request, response, false);
        }

        protected void check() throws IOException, ServletException {
            String value = fixEmpty(request.getParameter("value"));
            AbstractProject<?,?> p = Hudson.getInstance().getItemByFullName(request.getParameter("job"),AbstractProject.class);

            if(value==null || p==null) {
                ok(); // none entered yet, or something is seriously wrong
                return;
            }

            try {
                FilePath ws = p.getWorkspace();

                if(!ws.exists()) {// no workspace. can't check
                    ok();
                    return;
                }

                error(ws.validateAntFileMask(value));
            } catch (InterruptedException e) {
                ok(); // coundn't check
            }
        }
    }
}
