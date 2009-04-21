package hudson.scm;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Extension;
import hudson.model.Hudson;
import hudson.scm.SubversionSCM.DescriptorImpl.Credential;
import org.tmatesoft.svn.core.SVNURL;

/**
 * Extension point for programmatically providing a credential (such as username/password) for
 * Subversion access.
 *
 * <p>
 * Put {@link Extension} on your implementation to have it registered.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.301
 */
public abstract class SubversionCredentialProvider implements ExtensionPoint {
    /**
     * Called whenever Hudson needs to connect to an authenticated subversion repository,
     * to obtain a credential.
     *
     * @param realm
     *      This is a non-null string that represents the realm of authentication.
     * @param url
     *      URL that is being accessed. Never null.
     * @return
     *      null if the implementation doesn't understand the given realm. When null is returned,
     *      Hudson searches other sources of credentials to come up with one.
     */
    public abstract Credential getCredential(SVNURL url, String realm);

    /**
     * All regsitered instances.
     */
    public static ExtensionList<SubversionCredentialProvider> all() {
        return Hudson.getInstance().getExtensionList(SubversionCredentialProvider.class);
    }
}
