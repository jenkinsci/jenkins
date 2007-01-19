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
