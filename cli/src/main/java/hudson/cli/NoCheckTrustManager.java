package hudson.cli;

import javax.net.ssl.TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * @author Kohsuke Kawaguchi
 */
public class NoCheckTrustManager implements TrustManager {
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
    }

    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
    }

    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
