package jenkins.util;

import com.trilead.ssh2.crypto.Base64;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.jvnet.hudson.crypto.CertificateUtil;
import org.jvnet.hudson.crypto.SignatureOutputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 * @since 1.482
 */
public class JSONSignatureValidator {
    private final String name;

    public JSONSignatureValidator(String name) {
        this.name = name;
    }

    /**
     * Verifies the signature in the update center data file.
     */
    public FormValidation verifySignature(JSONObject o) throws IOException {
        try {
            FormValidation warning = null;

            JSONObject signature = o.getJSONObject("signature");
            if (signature.isNullObject()) {
                return FormValidation.error("No signature block found in "+name);
            }
            o.remove("signature");

            List<X509Certificate> certs = new ArrayList<X509Certificate>();
            {// load and verify certificates
                CertificateFactory cf = CertificateFactory.getInstance("X509");
                for (Object cert : signature.getJSONArray("certificates")) {
                    X509Certificate c = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(Base64.decode(cert.toString().toCharArray())));
                    try {
                        c.checkValidity();
                    } catch (CertificateExpiredException e) { // even if the certificate isn't valid yet, we'll proceed it anyway
                        warning = FormValidation.warning(e,String.format("Certificate %s has expired in %s",cert.toString(),name));
                    } catch (CertificateNotYetValidException e) {
                        warning = FormValidation.warning(e,String.format("Certificate %s is not yet valid in %s",cert.toString(),name));
                    }
                    LOGGER.log(Level.FINE, "Add certificate found in json doc: \r\n\tsubjectDN: {0}\r\n\tissuer: {1}", new Object[]{c.getSubjectDN(), c.getIssuerDN()});
                    certs.add(c);
                }

                CertificateUtil.validatePath(certs, loadTrustAnchors(cf));
            }

            // this is for computing a digest to check sanity
            MessageDigest sha1 = MessageDigest.getInstance("SHA1");
            DigestOutputStream dos = new DigestOutputStream(new NullOutputStream(),sha1);

            // this is for computing a signature
            Signature sig = Signature.getInstance("SHA1withRSA");
            sig.initVerify(certs.get(0));
            SignatureOutputStream sos = new SignatureOutputStream(sig);

            // until JENKINS-11110 fix, UC used to serve invalid digest (and therefore unverifiable signature)
            // that only covers the earlier portion of the file. This was caused by the lack of close() call
            // in the canonical writing, which apparently leave some bytes somewhere that's not flushed to
            // the digest output stream. This affects Jenkins [1.424,1,431].
            // Jenkins 1.432 shipped with the "fix" (1eb0c64abb3794edce29cbb1de50c93fa03a8229) that made it
            // compute the correct digest, but it breaks all the existing UC json metadata out there. We then
            // quickly discovered ourselves in the catch-22 situation. If we generate UC with the correct signature,
            // it'll cut off [1.424,1.431] from the UC. But if we don't, we'll cut off [1.432,*).
            //
            // In 1.433, we revisited 1eb0c64abb3794edce29cbb1de50c93fa03a8229 so that the original "digest"/"signature"
            // pair continues to be generated in a buggy form, while "correct_digest"/"correct_signature" are generated
            // correctly.
            //
            // Jenkins should ignore "digest"/"signature" pair. Accepting it creates a vulnerability that allows
            // the attacker to inject a fragment at the end of the json.
            o.writeCanonical(new OutputStreamWriter(new TeeOutputStream(dos,sos),"UTF-8")).close();

            // did the digest match? this is not a part of the signature validation, but if we have a bug in the c14n
            // (which is more likely than someone tampering with update center), we can tell
            String computedDigest = new String(Base64.encode(sha1.digest()));
            String providedDigest = signature.optString("correct_digest");
            if (providedDigest==null) {
                return FormValidation.error("No correct_digest parameter in "+name+". This metadata appears to be old.");
            }
            if (!computedDigest.equalsIgnoreCase(providedDigest)) {
                String msg = "Digest mismatch: computed=" + computedDigest + " vs expected=" + providedDigest + " in " + name;
                if (LOGGER.isLoggable(Level.SEVERE)) {
                    LOGGER.severe(msg);
                    LOGGER.severe(o.toString(2));
                }
                return FormValidation.error(msg);
            }

            String providedSignature = signature.getString("correct_signature");
            if (!sig.verify(Base64.decode(providedSignature.toCharArray()))) {
                return FormValidation.error("Signature in the update center doesn't match with the certificate in "+name);
            }

            if (warning!=null)  return warning;
            return FormValidation.ok();
        } catch (GeneralSecurityException e) {
            return FormValidation.error(e,"Signature verification failed in "+name);
        }
    }

    protected Set<TrustAnchor> loadTrustAnchors(CertificateFactory cf) throws IOException {
        // if we trust default root CAs, we end up trusting anyone who has a valid certificate,
        // which isn't useful at all
        Set<TrustAnchor> anchors = new HashSet<TrustAnchor>(); // CertificateUtil.getDefaultRootCAs();
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            return anchors;
        }
        for (String cert : (Set<String>) j.servletContext.getResourcePaths("/WEB-INF/update-center-rootCAs")) {
            if (cert.endsWith("/") || cert.endsWith(".txt"))  {
                continue;       // skip directories also any text files that are meant to be documentation
            }
            InputStream in = j.servletContext.getResourceAsStream(cert);
            if (in == null) continue; // our test for paths ending in / should prevent this from happening
            Certificate certificate;
            try {
                certificate = cf.generateCertificate(in);
            } catch (CertificateException e) {
                LOGGER.log(Level.WARNING, String.format("Webapp resources in /WEB-INF/update-center-rootCAs are "
                                + "expected to be either certificates or .txt files documenting the "
                                + "certificates, but %s did not parse as a certificate. Skipping this "
                                + "resource for now.",
                        cert), e);
                continue;
            } finally {
                in.close();
            }
            try {
                TrustAnchor certificateAuthority = new TrustAnchor((X509Certificate) certificate, null);
                LOGGER.log(Level.FINE, "Add Certificate Authority {0}: {1}",
                        new Object[]{cert, (certificateAuthority.getTrustedCert() == null ? null : certificateAuthority.getTrustedCert().getSubjectDN())});
                anchors.add(certificateAuthority);
            } catch (IllegalArgumentException e) {
                LOGGER.log(Level.WARNING,
                        String.format("The name constraints in the certificate resource %s could not be "
                                        + "decoded. Skipping this resource for now.",
                        cert), e);
            }
        }
        File[] cas = new File(j.root, "update-center-rootCAs").listFiles();
        if (cas!=null) {
            for (File cert : cas) {
                if (cert.isDirectory() || cert.getName().endsWith(".txt"))  {
                    continue;       // skip directories also any text files that are meant to be documentation
                }
                FileInputStream in = new FileInputStream(cert);
                Certificate certificate;
                try {
                    certificate = cf.generateCertificate(in);
                } catch (CertificateException e) {
                    LOGGER.log(Level.WARNING, String.format("Files in %s are expected to be either "
                                    + "certificates or .txt files documenting the certificates, "
                                    + "but %s did not parse as a certificate. Skipping this file for now.",
                            cert.getParentFile().getAbsolutePath(),
                            cert.getAbsolutePath()), e);
                    continue;
                } finally {
                    in.close();
                }
                try {
                    TrustAnchor certificateAuthority = new TrustAnchor((X509Certificate) certificate, null);
                    LOGGER.log(Level.FINE, "Add Certificate Authority {0}: {1}",
                            new Object[]{cert, (certificateAuthority.getTrustedCert() == null ? null : certificateAuthority.getTrustedCert().getSubjectDN())});
                    anchors.add(certificateAuthority);
                } catch (IllegalArgumentException e) {
                    LOGGER.log(Level.WARNING,
                            String.format("The name constraints in the certificate file %s could not be "
                                            + "decoded. Skipping this file for now.",
                            cert.getAbsolutePath()), e);
                }
            }
        }
        return anchors;
    }

    private static final Logger LOGGER = Logger.getLogger(JSONSignatureValidator.class.getName());
}
