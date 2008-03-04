package hudson;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import java.io.IOException;

/**
 * Obtains the structured form data from {@link StaplerRequest}.
 * See http://hudson.gotdns.com/wiki/display/HUDSON/Structured+Form+Submission
 *
 * @author Kohsuke Kawaguchi
 */
public class StructuredForm {
    public static JSONObject get(StaplerRequest req) {
        JSONObject o = (JSONObject)req.getAttribute(StructuredForm.class.getName());
        if(o==null) {
            String p = req.getParameter("json");
            if(p==null) {
                // no data submitted
                try {
                    StaplerResponse rsp = Stapler.getCurrentResponse();
                    rsp.sendError(SC_BAD_REQUEST,"This page expects a form submission");
                    rsp.getWriter().close();
                    throw new Error("This page expects a form submission");
                } catch (IOException e) {
                    throw new Error(e);
                }
            }
            o = JSONObject.fromObject(p);
            req.setAttribute(StructuredForm.class.getName(),o);
        }
        return o;
    }
}
