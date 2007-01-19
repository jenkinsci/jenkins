package hudson.model;

import hudson.maven.MavenModuleSet;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class TopLevelItems {
    /**
     * List of all installed job types.
     */
    public static final List<TopLevelItemDescriptor> LIST = (List)Descriptor.toList(
        Project.DESCRIPTOR,
        ExternalJob.DESCRIPTOR
    );

    public static TopLevelItemDescriptor getDescriptor(String displayName) {
        for (TopLevelItemDescriptor job : LIST) {
            if(job.getDisplayName().equals(displayName))
                return job;
        }
        return null;
    }

    static {
        if(Boolean.getBoolean("hudson.maven"))
            LIST.add(MavenModuleSet.DESCRIPTOR);
    }
}
