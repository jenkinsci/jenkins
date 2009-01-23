package hudson.model;

import org.kohsuke.stapler.StaplerRequest;

import java.util.List;
import java.util.ArrayList;

/**
 * {@link Descriptor} for {@link TopLevelItem}s.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class TopLevelItemDescriptor extends Descriptor<TopLevelItem> {
    protected TopLevelItemDescriptor(Class<? extends TopLevelItem> clazz) {
        super(clazz);
    }

    /**
     * Infers the type of the corresponding {@link TopLevelItem} from the outer class.
     * This version works when you follow the common convention, where a descriptor
     * is written as the static nested class of the describable class.
     *
     * @since 1.278
     */
    protected TopLevelItemDescriptor() {
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Used as the caption when the user chooses what job type to create.
     * The descriptor implementation also needs to have <tt>detail.jelly</tt>
     * script, which will be used to render the text below the caption
     * that expains the job type.
     */
    public abstract String getDisplayName();

    public final String newInstanceDetailPage() {
        return '/'+clazz.getName().replace('.','/').replace('$','/')+"/newJobDetail.jelly";
    }

    /**
     * @deprecated
     *      This is not a valid operation for {@link Job}s.
     */
    @Deprecated
    public TopLevelItem newInstance(StaplerRequest req) throws FormException {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a new {@link Job}.
     */
    public abstract TopLevelItem newInstance(String name);
}
