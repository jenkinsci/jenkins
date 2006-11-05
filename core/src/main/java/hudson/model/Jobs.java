package hudson.model;

import java.util.List;

/**
 * List of all installed {@link Job} types.
 * 
 * @author Kohsuke Kawaguchi
 */
public class Jobs {
    /**
     * List of all installed job types.
     */
    public static final List<JobDescriptor> JOBS = (List)Descriptor.toList(
        Project.DESCRIPTOR,
        ExternalJob.DESCRIPTOR
    );

    public static JobDescriptor getDescriptor(String displayName) {
        for (JobDescriptor job : JOBS) {
            if(job.getDisplayName().equals(displayName))
                return job;
        }
        return null;
    }
}
