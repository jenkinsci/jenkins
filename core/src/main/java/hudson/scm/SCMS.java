package hudson.scm;

import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;

import java.util.List;

import org.kohsuke.stapler.StaplerRequest;

/**
 * List of all installed SCMs.
 * 
 * @author Kohsuke Kawaguchi
 */
public class SCMS {
    /**
     * List of all installed SCMs.
     */
    @SuppressWarnings("unchecked") // generic array creation
    public static final List<SCMDescriptor> SCMS =
        Descriptor.toList(
            NullSCM.DESCRIPTOR,
            CVSSCM.DescriptorImpl.DESCRIPTOR,
            SubversionSCM.DescriptorImpl.DESCRIPTOR);

    /**
     * Parses {@link SCM} configuration from the submitted form.
     */
    public static SCM parseSCM(StaplerRequest req) throws FormException {
        int scmidx = Integer.parseInt(req.getParameter("scm"));
        return SCMS.get(scmidx).newInstance(req);
    }
}
