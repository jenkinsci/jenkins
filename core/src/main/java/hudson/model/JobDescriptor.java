package hudson.model;

import org.kohsuke.stapler.StaplerRequest;

/**
 * {@link Descriptor} for {@link Job}s.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class JobDescriptor<J extends Job<J,R>,R extends Run<J,R>> extends Descriptor<Job<J,R>> {
    protected JobDescriptor(Class<? extends Job<J,R>> clazz) {
        super(clazz);
    }

    /**
     * @deprecated
     *      This is not a valid operation for {@link Job}s.
     */
    public Job<J,R> newInstance(StaplerRequest req) throws FormException {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a new {@link Job}.
     */
    public abstract Job<J,R> newInstance(String name);
}
