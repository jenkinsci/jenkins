package jenkins.model;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;

/**
 * {@link Descriptor} for {@link BuildDiscarder}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class BuildDiscarderDescriptor extends Descriptor<BuildDiscarder> {
    protected BuildDiscarderDescriptor(Class clazz) {
        super(clazz);
    }

    protected BuildDiscarderDescriptor() {
    }

    /**
     * Returns all the registered {@link BuildDiscarderDescriptor}s.
     */
    public static DescriptorExtensionList<BuildDiscarder,BuildDiscarderDescriptor> all() {
        return Jenkins.getInstance().<BuildDiscarder,BuildDiscarderDescriptor>getDescriptorList(BuildDiscarder.class);
    }
}
