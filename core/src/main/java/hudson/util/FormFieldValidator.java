package hudson.util;

import hudson.Util;
import hudson.model.Hudson;
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
}
