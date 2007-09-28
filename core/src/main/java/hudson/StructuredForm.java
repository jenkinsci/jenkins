package hudson;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

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
            o = JSONObject.fromObject(req.getParameter("json"));
            req.setAttribute(StructuredForm.class.getName(),o);
        }
        return o;
    }
}
