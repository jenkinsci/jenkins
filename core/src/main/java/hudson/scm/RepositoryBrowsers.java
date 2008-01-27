package hudson.scm;

import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.scm.browsers.*;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.List;

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
     */
    public static <T extends RepositoryBrowser>
    T createInstance(Class<T> type, StaplerRequest req, String fieldName) throws FormException {
        List<Descriptor<RepositoryBrowser<?>>> list = filter(type);
        String value = req.getParameter(fieldName);
        if(value==null || value.equals("auto"))
            return null;

        return type.cast(list.get(Integer.parseInt(value)).newInstance(req,null/*TODO*/));
    }
}
