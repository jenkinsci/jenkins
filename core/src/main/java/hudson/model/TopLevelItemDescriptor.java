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
     * {@inheritDoc}
     *
     * <p>
     * Used as the caption when the user chooses what job type to create.
     * The descriptor implementation also needs to have <tt>detail.jelly</tt>
     * script, which will be used to render the text below the caption
     * that expains the job type.
     */
    public abstract String getDisplayName();

    public final String getNewJobDetailPage() {
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
