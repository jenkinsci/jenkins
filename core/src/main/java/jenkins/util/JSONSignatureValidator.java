package jenkins.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import hudson.util.FormValidation;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.io.output.TeeOutputStream;
import org.jvnet.hudson.crypto.CertificateUtil;
import org.jvnet.hudson.crypto.SignatureOutputStream;

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
    @SuppressFBWarnings(value = "WEAK_MESSAGE_DIGEST_SHA1", justification = "SHA-1 is only used as a fallback if SHA-512 is not available")
    public FormValidation verifySignature(JSONObject o) throws IOException {
        try {
            FormValidation warning = null;

            JSONObject signature = o.getJSONObject("signature");
            if (signature.isNullObject()) {
                return FormValidation.error("No signature block found in " + name);
            }
            o.remove("signature");

            List<X509Certificate> certs = new ArrayList<>();
            { // load and verify certificates
                CertificateFactory cf = CertificateFactory.getInstance("X509");
                for (Object cert : signature.getJSONArray("certificates")) {
                    try {
                        X509Certificate c = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(cert.toString().getBytes(StandardCharsets.UTF_8))));
                        try {
                            c.checkValidity();
                        } catch (CertificateExpiredException e) { // even if the certificate isn't valid yet, we'll proceed it anyway
                            warning = FormValidation.warning(e, String.format("Certificate %s has expired in %s", cert, name));
                        } catch (CertificateNotYetValidException e) {
                            warning = FormValidation.warning(e, String.format("Certificate %s is not yet valid in %s", cert, name));
                        }
                        LOGGER.log(Level.FINE, "Add certificate found in JSON document:\n\tsubjectDN: {0}\n\tissuer: {1}\n\tnotBefore: {2}\n\tnotAfter: {3}",
                                new Object[] { c.getSubjectDN(), c.getIssuerDN(), c.getNotBefore(), c.getNotAfter() });
                        LOGGER.log(Level.FINEST, () -> "Certificate from JSON document: " + c);
                        certs.add(c);
                    } catch (IllegalArgumentException ex) {
                        throw new IOException("Could not decode certificate", ex);
                    }
                }

                CertificateUtil.validatePath(certs, loadTrustAnchors(cf));
            }

            if (certs.isEmpty()) {
                return FormValidation.error("No certificate found in %s. Cannot verify the signature", name);
            }

            // check the better digest first
            FormValidation resultSha512 = null;
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-512");
                Signature sig = Signature.getInstance("SHA512withRSA");
                sig.initVerify(certs.getFirst());
                resultSha512 = checkSpecificSignature(o, signature, digest, "correct_digest512", sig, "correct_signature512", "SHA-512");
                switch (resultSha512.kind) {
                    case ERROR:
                        return resultSha512;
                    case WARNING:
                        LOGGER.log(Level.INFO, "JSON data source '" + name + "' does not provide a SHA-512 content checksum or signature. Looking for SHA-1.");
                        break;
                    case OK:
                        break;
                    default:
                        throw new AssertionError("Unknown form validation kind: " + resultSha512.kind);
                }
            } catch (NoSuchAlgorithmException nsa) {
                LOGGER.log(Level.WARNING, "Failed to verify potential SHA-512 digest/signature, falling back to SHA-1", nsa);
            }

            // if we get here, SHA-512 passed, wasn't provided, or the JRE is terrible.

            MessageDigest digest = MessageDigest.getInstance("SHA1");
            Signature sig = Signature.getInstance("SHA1withRSA");
            sig.initVerify(certs.getFirst());
            FormValidation resultSha1 = checkSpecificSignature(o, signature, digest, "correct_digest", sig, "correct_signature", "SHA-1");

            switch (resultSha1.kind) {
                case ERROR:
                    return resultSha1;
                case WARNING:
                    if (resultSha512.kind == FormValidation.Kind.WARNING) {
                        // neither signature provided
                        return FormValidation.error("No correct_signature or correct_signature512 entry found in '" + name + "'.");
                    }
                case OK:
                    break;
                default:
                    throw new AssertionError("Unknown form validation kind: " + resultSha1.kind);
            }

            if (warning != null)  return warning;
            return FormValidation.ok();
        } catch (GeneralSecurityException e) {
            // Return a user-friendly error message without the full stack trace
            String rootCauseMessage = getRootCauseMessage(e);
            return FormValidation.error("Signature verification failed in " + name + ": " + rootCauseMessage);
        }
    }


    /**
     * Computes the specified {@code digest} and {@code signature} for the provided {@code json} object and checks whether they match {@code digestEntry} and {@code signatureEntry} in the provided {@code signatureJson} object.
     *
     * @param json the full update-center.json content
     * @param signatureJson signature block from update-center.json
     * @param digest digest to compute
     * @param digestEntry key of the digest entry in {@code signatureJson} to check
     * @param signature signature to compute
     * @param signatureEntry key of the signature entry in {@code signatureJson} to check
     * @param digestName name of the digest used for log/error messages
     * @return {@link FormValidation.Kind#WARNING} if digest or signature are not provided, {@link FormValidation.Kind#OK} if check is successful, {@link FormValidation.Kind#ERROR} otherwise.
     * @throws IOException if this somehow fails to write the canonical JSON representation to an in-memory stream.
     */
    private FormValidation checkSpecificSignature(JSONObject json, JSONObject signatureJson, MessageDigest digest, String digestEntry, Signature signature, String signatureEntry, String digestName) throws IOException {
        // this is for computing a digest to check sanity
        OutputStream nos = OutputStream.nullOutputStream();
        DigestOutputStream dos = new DigestOutputStream(nos, digest);
        SignatureOutputStream sos = new SignatureOutputStream(signature);

        String providedDigest = signatureJson.optString(digestEntry, null);
        if (providedDigest == null) {
            return FormValidation.warning("No '" + digestEntry + "' found");
        }

        String providedSignature = signatureJson.optString(signatureEntry, null);
        if (providedSignature == null) {
            return FormValidation.warning("No '" + signatureEntry + "' found");
        }

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
        json.writeCanonical(new OutputStreamWriter(new TeeOutputStream(dos, sos), StandardCharsets.UTF_8)).close();

        // did the digest match? this is not a part of the signature validation, but if we have a bug in the c14n
        // (which is more likely than someone tampering with update center), we can tell

        if (!digestMatches(digest.digest(), providedDigest)) {
            String msg = digestName + " digest mismatch: expected=" + providedDigest + " in '" + name + "'";
            if (LOGGER.isLoggable(Level.SEVERE)) {
                LOGGER.severe(msg);
                LOGGER.severe(json.toString(2));
            }
            return FormValidation.error(msg);
        }

        if (!verifySignature(signature, providedSignature)) {
            return FormValidation.error(digestName + " based signature in the update center doesn't match with the certificate in '" + name + "'");
        }

        return FormValidation.ok();
    }

    /**
     * Utility method supporting both possible signature formats: Base64 and Hex
     */
    private boolean verifySignature(Signature signature, String providedSignature) {
        // We can only make one call to Signature#verify here.
        // Since we need to potentially check two values (one decoded from hex, the other decoded from base64),
        // try hex first: It's almost certainly going to fail decoding if a base64 string was passed.
        // It is extremely unlikely for base64 strings to be a valid hex string.
        // This way, if it's base64, the #verify call will be skipped, and we continue with the #verify for decoded base64.
        // This approach might look unnecessarily clever, but short of having redundant Signature instances,
        // there doesn't seem to be a better approach for this.
        try {
            if (signature.verify(Util.fromHexString(providedSignature))) {
                return true;
            }
        } catch (SignatureException | IllegalArgumentException ignore) {
            // ignore
        }

        try {
            if (signature.verify(Base64.getDecoder().decode(providedSignature))) {
                return true;
            }
        } catch (SignatureException | IllegalArgumentException ignore) {
            // ignore
        }
        return false;
    }

    /**
     * Utility method supporting both possible digest formats: Base64 and Hex
     */
    private boolean digestMatches(byte[] digest, String providedDigest) {
        return providedDigest.equalsIgnoreCase(Util.toHexString(digest)) || providedDigest.equalsIgnoreCase(Base64.getEncoder().encodeToString(digest));
    }


    protected Set<TrustAnchor> loadTrustAnchors(CertificateFactory cf) throws IOException {
        // if we trust default root CAs, we end up trusting anyone who has a valid certificate,
        // which isn't useful at all
        Set<TrustAnchor> anchors = new HashSet<>(); // CertificateUtil.getDefaultRootCAs();
        Jenkins j = Jenkins.get();
        for (String cert : j.getServletContext().getResourcePaths("/WEB-INF/update-center-rootCAs")) {
            if (cert.endsWith("/") || cert.endsWith(".txt"))  {
                continue;       // skip directories also any text files that are meant to be documentation
            }
            Certificate certificate;
            try (InputStream in = j.getServletContext().getResourceAsStream(cert)) {
                if (in == null) continue; // our test for paths ending in / should prevent this from happening
                certificate = cf.generateCertificate(in);
                if (certificate instanceof X509Certificate c) {
                    LOGGER.log(Level.FINE, "Add CA certificate found in webapp resources:\n\tsubjectDN: {0}\n\tissuer: {1}\n\tnotBefore: {2}\n\tnotAfter: {3}",
                            new Object[] { c.getSubjectDN(), c.getIssuerDN(), c.getNotBefore(), c.getNotAfter() });
                }
                LOGGER.log(Level.FINEST, () -> "CA certificate from webapp resource " + cert + ": " + certificate);
            } catch (CertificateException e) {
                LOGGER.log(Level.WARNING, String.format("Webapp resources in /WEB-INF/update-center-rootCAs are "
                                + "expected to be either certificates or .txt files documenting the "
                                + "certificates, but %s did not parse as a certificate. Skipping this "
                                + "resource for now.",
                        cert), e);
                continue;
            }
            try {
                TrustAnchor certificateAuthority = new TrustAnchor((X509Certificate) certificate, null);
                anchors.add(certificateAuthority);
            } catch (IllegalArgumentException e) {
                LOGGER.log(Level.WARNING,
                        String.format("The name constraints in the certificate resource %s could not be "
                                        + "decoded. Skipping this resource for now.",
                        cert), e);
            }
        }
        File[] cas = new File(j.root, "update-center-rootCAs").listFiles();
        if (cas != null) {
            for (File cert : cas) {
                if (cert.isDirectory() || cert.getName().endsWith(".txt"))  {
                    continue;       // skip directories also any text files that are meant to be documentation
                }
                Certificate certificate;
                try (InputStream in = Files.newInputStream(cert.toPath())) {
                    certificate = cf.generateCertificate(in);
                    if (certificate instanceof X509Certificate c) {
                        LOGGER.log(Level.FINE, "Add CA certificate found in Jenkins home:\n\tsubjectDN: {0}\n\tissuer: {1}\n\tnotBefore: {2}\n\tnotAfter: {3}",
                                new Object[] { c.getSubjectDN(), c.getIssuerDN(), c.getNotBefore(), c.getNotAfter() });
                    }
                    LOGGER.log(Level.FINEST, () -> "CA certificate from Jenkins home " + cert + ": " + certificate);
                } catch (InvalidPathException e) {
                    throw new IOException(e);
                } catch (CertificateException e) {
                    LOGGER.log(Level.WARNING, String.format("Files in %s are expected to be either "
                                    + "certificates or .txt files documenting the certificates, "
                                    + "but %s did not parse as a certificate. Skipping this file for now.",
                            cert.getParentFile().getAbsolutePath(),
                            cert.getAbsolutePath()), e);
                    continue;
                }
                try {
                    TrustAnchor certificateAuthority = new TrustAnchor((X509Certificate) certificate, null);
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

    /**
     * Extracts a user-friendly message from an exception chain.
     *
     * @param e the exception to extract the message from
     * @return a concise, readable error message
     */
    private String getRootCauseMessage(@NonNull Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }

        String message = cause.getMessage();
        if (message != null && !message.isEmpty()) {
            return message;
        }

        return cause.getClass().getSimpleName();
    }

    private static final Logger LOGGER = Logger.getLogger(JSONSignatureValidator.class.getName());
}
