package hudson.scm;

import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.scm.browsers.*;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.List;

import net.sf.json.JSONObject;
import net.sf.json.JSONArray;

/**
 * List of all installed {@link RepositoryBrowsers}.
 *
 * @author Kohsuke Kawaguchi
 */
public class RepositoryBrowsers {
    /**
     * List of all installed {@link RepositoryBrowsers}.
     */
    public static final List<Descriptor<RepositoryBrowser<?>>> LIST = Descriptor.toList(
        ViewCVS.DESCRIPTOR,
        ViewSVN.DescriptorImpl.INSTANCE,
        FishEyeSVN.DESCRIPTOR,
        FishEyeCVS.DESCRIPTOR,
        WebSVN.DESCRIPTOR,
        Sventon.DESCRIPTOR,
        CollabNetSVN.DESCRIPTOR
    );

    /**
     * Only returns those {@link RepositoryBrowser} descriptors that extend from the given type.
     */
    public static List<Descriptor<RepositoryBrowser<?>>> filter(Class<? extends RepositoryBrowser> t) {
        List<Descriptor<RepositoryBrowser<?>>> r = new ArrayList<Descriptor<RepositoryBrowser<?>>>();
        for (Descriptor<RepositoryBrowser<?>> d : LIST)
            if(t.isAssignableFrom(d.clazz))
                r.add(d);
        return r;
    }

    /**
     * Creates an instance of {@link RepositoryBrowser} from a form submission.
     *
     * @deprecated
     *      Use {@link #createInstance(Class, StaplerRequest, JSONObject, String)}.
     */
    public static <T extends RepositoryBrowser>
    T createInstance(Class<T> type, StaplerRequest req, String fieldName) throws FormException {
        List<Descriptor<RepositoryBrowser<?>>> list = filter(type);
        String value = req.getParameter(fieldName);
        if(value==null || value.equals("auto"))
            return null;

        return type.cast(list.get(Integer.parseInt(value)).newInstance(req,null/*TODO*/));
    }

    /**
     * Creates an instance of {@link RepositoryBrowser} from a form submission.
     *
     * @since 1.227
     */
    public static <T extends RepositoryBrowser>
    T createInstance(Class<T> type, StaplerRequest req, JSONObject parent, String fieldName) throws FormException {
        List<Descriptor<RepositoryBrowser<?>>> list = filter(type);

        Object o = parent.get(fieldName);
        if(o==null) return null;

        if(o instanceof String && "auto".equals(o))
            return null;

        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            return type.cast(list.get(a.getInt(0)).newInstance(req,a.getJSONObject(1)));
        }

        throw new AssertionError(o.getClass()+" : "+o);
    }
}
