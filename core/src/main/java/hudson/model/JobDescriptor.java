package hudson.model;

import org.kohsuke.stapler.StaplerRequest;

import java.util.List;
import java.util.ArrayList;

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
    @Deprecated
    public Job<J,R> newInstance(StaplerRequest req) throws FormException {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a new {@link Job}.
     */
    public abstract Job<J,R> newInstance(String name);

    /**
     * Gets the {@link JobPropertyDescriptor}s applicable for this job type.
     */
    public List<JobPropertyDescriptor> getPropertyDescriptors() {
        List<JobPropertyDescriptor> r = new ArrayList<JobPropertyDescriptor>();
        for (JobPropertyDescriptor p : Jobs.PROPERTIES)
            if(p.isApplicable(clazz))
                r.add(p);
        return r;
    }
}
