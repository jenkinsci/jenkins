package hudson.model;

import java.util.List;

/**
 * List of all installed {@link Job} types.
 * 
 * @author Kohsuke Kawaguchi
 */
public class Jobs {
    /**
     * List of all installed {@link JobPropertyDescriptor} types.
     *
     * <p>
     * Plugins can add their {@link JobPropertyDescriptor}s to this list.
     *
     * @see JobPropertyDescriptor#getPropertyDescriptors(Class)
     */
    public static final List<JobPropertyDescriptor> PROPERTIES = Descriptor.toList(
    );
}
