package hudson.cli;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * @author Kohsuke Kawaguchi
 */
@SuppressFBWarnings(value = "WEAK_TRUST_MANAGER", justification = "User set parameter to skip verifier.")
public class NoCheckTrustManager implements X509TrustManager {
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
    }

    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
    }

    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
