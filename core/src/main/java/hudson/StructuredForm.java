package hudson;

import net.sf.json.JSONObject;
import net.sf.json.JSONArray;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.Collections;

/**
 * Obtains the structured form data from {@link StaplerRequest}.
 * See http://hudson.gotdns.com/wiki/display/HUDSON/Structured+Form+Submission
 *
 * @author Kohsuke Kawaguchi
 */
public class StructuredForm {

    /**
     * @deprecated
     *      Use {@link StaplerRequest#getSubmittedForm()}. Since 1.238.
     */
    public static JSONObject get(StaplerRequest req) throws ServletException {
        return req.getSubmittedForm();
    }
    /**
     * Retrieves the property of the given object and returns it as a list of {@link JSONObject}.
     *
     * <p>
     * If the value doesn't exist, this method returns an empty list. If the value is
     * a {@link JSONObject}, this method will return a singleton list. If it's a {@link JSONArray},
     * the contents will be returned as a list.
     *
     * <p>
     * Because of the way structured form submission work, this is convenient way of
     * handling repeated multi-value entries.
     *
     * @since 1.233 
     */
    public static List<JSONObject> toList(JSONObject parent, String propertyName) {
        Object v = parent.get(propertyName);
        if(v==null)
            return Collections.emptyList();
        if(v instanceof JSONObject)
            return Collections.singletonList((JSONObject)v);
        if(v instanceof JSONArray)
            return (JSONArray)v;

        throw new IllegalArgumentException();
    }
}
