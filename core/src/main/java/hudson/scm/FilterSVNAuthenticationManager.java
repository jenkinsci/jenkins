package hudson.scm;

import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.ISVNProxyManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.io.SVNRepository;

import javax.net.ssl.TrustManager;

/**
 * {@link ISVNAuthenticationManager} filter. Useful for customizing the behavior by delegation.
 * @author Kohsuke Kawaguchi
 */
public class FilterSVNAuthenticationManager implements ISVNAuthenticationManager {
    protected ISVNAuthenticationManager core;

    public FilterSVNAuthenticationManager(ISVNAuthenticationManager core) {
        this.core = core;
    }

    public void setAuthenticationProvider(ISVNAuthenticationProvider provider) {
        core.setAuthenticationProvider(provider);
    }

    public ISVNProxyManager getProxyManager(SVNURL url) throws SVNException {
        return core.getProxyManager(url);
    }

    public TrustManager getTrustManager(SVNURL url) throws SVNException {
        return core.getTrustManager(url);
    }

    public SVNAuthentication getFirstAuthentication(String kind, String realm, SVNURL url) throws SVNException {
        return core.getFirstAuthentication(kind, realm, url);
    }

    public SVNAuthentication getNextAuthentication(String kind, String realm, SVNURL url) throws SVNException {
        return core.getNextAuthentication(kind, realm, url);
    }

    public void acknowledgeAuthentication(boolean accepted, String kind, String realm, SVNErrorMessage errorMessage, SVNAuthentication authentication) throws SVNException {
        core.acknowledgeAuthentication(accepted, kind, realm, errorMessage, authentication);
    }

    public void acknowledgeTrustManager(TrustManager manager) {
        core.acknowledgeTrustManager(manager);
    }

    public boolean isAuthenticationForced() {
        return core.isAuthenticationForced();
    }

    public int getReadTimeout(SVNRepository repository) {
        return core.getReadTimeout(repository);
    }

    public int getConnectTimeout(SVNRepository repository) {
        return core.getConnectTimeout(repository);
    }
}
