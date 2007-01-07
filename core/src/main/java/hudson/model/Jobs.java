package hudson.model;

import hudson.maven.MavenJob;

import java.util.List;
import java.util.ArrayList;

/**
 * List of all installed {@link Job} types.
 * 
 * @author Kohsuke Kawaguchi
 */
public class Jobs {
    /**
     * List of all installed job types.
     */
    @SuppressWarnings("unchecked") // two typing problems here!
    public static final List<JobDescriptor<?,?>> JOBS = (List)Descriptor.toList(
        Project.DESCRIPTOR,
        ExternalJob.DESCRIPTOR
    );

    public static JobDescriptor getDescriptor(String displayName) {
        for (JobDescriptor<?,?> job : JOBS) {
            if(job.getDisplayName().equals(displayName))
                return job;
        }
        return null;
    }

    /**
     * List of all installed {@link JobPropertyDescriptor} types.
     *
     * <p>
     * Plugins can add their {@link JobPropertyDescriptor}s to this list.
     *
     * @see JobDescriptor#getPropertyDescriptors() 
     */
    public static final List<JobPropertyDescriptor> PROPERTIES = Descriptor.toList(
    );

    static {
        if(Boolean.getBoolean("hudson.maven"))
            JOBS.add(MavenJob.DESCRIPTOR);
    }
}
