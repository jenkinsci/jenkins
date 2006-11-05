package hudson.model;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * {@link ModelObject} with some convenience methods.
 * 
 * @author Kohsuke Kawaguchi
 */
abstract class AbstractModelObject implements ModelObject {
    /**
     * Displays the error in a page.
     */
    protected final void sendError(Exception e, StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        sendError(e.getMessage(),req,rsp);
    }

    protected final void sendError(String message, StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        req.setAttribute("message",message);
        rsp.forward(this,"error",req);
    }
}
