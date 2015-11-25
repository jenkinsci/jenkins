package hudson.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.scm.SCMDescriptor;
import jenkins.ExtensionFilter;

import java.util.ArrayList;
import java.util.List;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Hides {@link Descriptor}s from users.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.393
 * @see ExtensionFilter
 */
public abstract class DescriptorVisibilityFilter implements ExtensionPoint {

    private static final Logger LOGGER = Logger.getLogger(DescriptorVisibilityFilter.class.getName());

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
        return ExtensionList.lookup(DescriptorVisibilityFilter.class);
    }

    public static <T extends Descriptor> List<T> apply(Object context, Iterable<T> source) {
        ExtensionList<DescriptorVisibilityFilter> filters = all();
        List<T> r = new ArrayList<T>();
        
        OUTER:
        for (T d : source) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Determining visibility of " + d + " in context " + context);
            }
            for (DescriptorVisibilityFilter f : filters) {
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.finer("Querying " + f + " for visibility of " + d + " in " + context);
                }
                if (!f.filter(context,d)) {
                    if (LOGGER.isLoggable(Level.CONFIG)) {
                       LOGGER.config("Filter " + f + " hides " + d + " in context " + context);
                    }
                    continue OUTER; // veto-ed. not shown
                }
            }
            r.add(d);
        }
        return r;
    }
}
