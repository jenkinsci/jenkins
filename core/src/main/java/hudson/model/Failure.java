package hudson.model;

import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Represents an error induced by user, encountered during HTTP request processing.
 *
 * <p>
 * The error page is rendered into HTML, but without a stack trace. So only use
 * this exception when the error condition is anticipated by the program, and where
 * we nor users don't need to see the stack trace to figure out the root cause. 
 *
 * @author Kohsuke Kawaguchi
 * @since 1.321
 */
public class Failure extends RuntimeException implements HttpResponse {
    private final boolean pre;

    public Failure(String message) {
        this(message,false);
    }

    public Failure(String message, boolean pre) {
        super(message);
        this.pre = pre;
    }

    public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
        req.setAttribute("message",getMessage());
        if(pre)
            req.setAttribute("pre",true);
        rsp.forward( node instanceof AbstractModelObject ? node : Hudson.getInstance() ,"error",req);
    }
}
