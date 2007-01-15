package hudson.scm;

import hudson.model.Descriptor;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class SCMS {
    /**
     * List of all installed SCMs.
     */
    @SuppressWarnings("unchecked") // generic array creation
    public static final List<Descriptor<SCM>> SCMS =
        Descriptor.toList(
            NullSCM.DESCRIPTOR,
            CVSSCM.DescriptorImpl.DESCRIPTOR,
            SubversionSCM.DescriptorImpl.DESCRIPTOR);
}
