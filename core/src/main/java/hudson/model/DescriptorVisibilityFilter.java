package hudson.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.scm.SCMDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * Hides {@link Descriptor}s from users.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.393
 */
public abstract class DescriptorVisibilityFilter implements ExtensionPoint {

    /**
     * Decides if the given descriptor should be visible to the user.
     *
     * @param context
     *      The object that indicates where the visibility of a descriptor is evaluated.
     *      For example, if Hudson is deciding whether a {@link FreeStyleProject} should gets a
     *      {@link SCMDescriptor}, the context object will be the {@link FreeStyleProject}.
     *      The caller can pass in null if there's no context.
     * @param descriptor
     *      Descriptor whose visibility is evaluated. Never null.
     *
     * @return
     *      true to allow the descriptor to be visible. false to hide it.
     *      If any of the installed {@link DescriptorVisibilityFilter} returns false,
     *      the descriptor is not shown.
     */
    public abstract boolean filter(Object context, Descriptor descriptor);

    public static ExtensionList<DescriptorVisibilityFilter> all() {
        return Hudson.getInstance().getExtensionList(DescriptorVisibilityFilter.class);
    }

    public static <T extends Descriptor> List<T> apply(Object context, Iterable<T> source) {
        ExtensionList<DescriptorVisibilityFilter> filters = all();
        List<T> r = new ArrayList<T>();
        
        OUTER:
        for (T d : source) {
            for (DescriptorVisibilityFilter f : filters) {
                if (!f.filter(context,d))
                    continue OUTER; // veto-ed. not shown
            }
            r.add(d);
        }
        return r;
    }
}
