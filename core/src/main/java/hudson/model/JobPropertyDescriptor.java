package hudson.model;

import org.kohsuke.stapler.StaplerRequest;

import java.util.List;
import java.util.ArrayList;

import net.sf.json.JSONObject;

/**
 * {@link Descriptor} for {@link JobProperty}.
 * 
 * @author Kohsuke Kawaguchi
 * @see Jobs#PROPERTIES
 * @since 1.72
 */
public abstract class JobPropertyDescriptor extends Descriptor<JobProperty<?>> {
    protected JobPropertyDescriptor(Class<? extends JobProperty<?>> clazz) {
        super(clazz);
    }

    /**
     * {@inheritDoc}
     *
     * @return
     *      null to avoid setting an instance of {@link JobProperty} to the target project.
     */
    public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        return super.newInstance(req, formData);
    }

    /**
     * Returns true if this {@link JobProperty} type is applicable to the
     * given job type.
     *
     * <p>
     * Normally, this method is implemented like
     * {@code return AbstractProject.class.isAssignableFrom(jobType)}
     * where "<tt>AbstractProject</tt>" is the J of {@link JobProperty}<tt>&lt;J></tt>.
     *
     * @return
     *      true to indicate applicable, in which case the property will be
     *      displayed in the configuration screen of this job.
     */
    public abstract boolean isApplicable(Class<? extends Job> jobType);

    /**
     * Gets the {@link JobPropertyDescriptor}s applicable for a given job type.
     */
    public static List<JobPropertyDescriptor> getPropertyDescriptors(Class<? extends Job> clazz) {
        List<JobPropertyDescriptor> r = new ArrayList<JobPropertyDescriptor>();
        for (JobPropertyDescriptor p : Jobs.PROPERTIES)
            if(p.isApplicable(clazz))
                r.add(p);
        return r;
    }
}
