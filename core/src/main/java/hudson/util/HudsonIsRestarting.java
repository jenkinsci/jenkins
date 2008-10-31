package hudson.util;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import java.io.IOException;

/**
 * Model object used to display "Hudson is restarting".
 * <p>
 * Set this object to {@link ServletContext#setAttribute(String, Object)} "app" while
 * the loading activity is taking place.
 *
 * @author Kohsuke Kawaguchi
 */
public class HudsonIsRestarting {
    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
        rsp.setStatus(SC_SERVICE_UNAVAILABLE);
        req.getView(this,"index.jelly").forward(req,rsp);
    }
}
