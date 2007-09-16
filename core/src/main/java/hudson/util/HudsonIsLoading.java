package hudson.util;

import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Model object used to display "Hudson is loading data"
 * @author Kohsuke Kawaguchi
 */
public class HudsonIsLoading {
    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
        rsp.setStatus(SC_SERVICE_UNAVAILABLE);
        req.getView(this,"index.jelly").forward(req,rsp);
    }
}
