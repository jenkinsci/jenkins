package hudson;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import java.io.IOException;

/**
 * Obtains the structured form data from {@link StaplerRequest}.
 * See http://hudson.gotdns.com/wiki/display/HUDSON/Structured+Form+Submission
 *
 * @author Kohsuke Kawaguchi
 */
public class StructuredForm {
    public static JSONObject get(StaplerRequest req) throws IOException, ServletException {
        JSONObject o = (JSONObject)req.getAttribute(StructuredForm.class.getName());
        if(o==null) {
            String p = req.getParameter("json");
            if(p==null) {
                // no data submitted
                StaplerResponse rsp = Stapler.getCurrentResponse();
                rsp.sendError(SC_BAD_REQUEST,"This page expects a form submission");
                rsp.getWriter().close();
                throw new ServletException("This page expects a form submission");
            }
            o = JSONObject.fromObject(p);
            req.setAttribute(StructuredForm.class.getName(),o);
        }
        return o;
    }
}
