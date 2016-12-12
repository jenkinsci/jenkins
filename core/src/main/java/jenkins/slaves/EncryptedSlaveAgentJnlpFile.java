package jenkins.slaves;

import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.slaves.SlaveComputer;
import hudson.util.Secret;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.ResponseImpl;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.compression.FilterServletOutputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * Serves the JNLP file.
 *
 * The client can request an encrypted payload (with JNLP MAC code as the key) or if the client has a suitable permission,
 * it can request a plain text payload.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.560
 */
public class EncryptedSlaveAgentJnlpFile implements HttpResponse {
    /**
     * The object that owns the Jelly view that renders JNLP file.
     * For example {@link SlaveComputer}.
     */
    private final AccessControlled it;
    /**
     * Name of the view that renders JNLP file that belongs to {@link #it}.
     */
    private final String viewName;
    /**
     * Name of the agent, which is used to determine secret HMAC code.
     */
    private final String slaveName;
    /**
     * Permission that allows plain text access. Checked against {@link #it}.
     */
    private final Permission connectPermission;

    public EncryptedSlaveAgentJnlpFile(AccessControlled it, String viewName, String slaveName, Permission connectPermission) {
        this.it = it;
        this.viewName = viewName;
        this.slaveName = slaveName;
        this.connectPermission = connectPermission;
    }

    @Override
    public void generateResponse(StaplerRequest req, StaplerResponse res, Object node) throws IOException, ServletException {
        RequestDispatcher view = req.getView(it, viewName);
        if ("true".equals(req.getParameter("encrypt"))) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            StaplerResponse temp = new ResponseImpl(req.getStapler(), new HttpServletResponseWrapper(res) {
                @Override public ServletOutputStream getOutputStream() throws IOException {
                    return new FilterServletOutputStream(baos);
                }
                @Override public PrintWriter getWriter() throws IOException {
                    throw new IllegalStateException();
                }
            });
            view.forward(req, temp);

            byte[] iv = new byte[128/8];
            new SecureRandom().nextBytes(iv);

            byte[] jnlpMac = JnlpSlaveAgentProtocol.SLAVE_SECRET.mac(slaveName.getBytes("UTF-8"));
            SecretKey key = new SecretKeySpec(jnlpMac, 0, /* export restrictions */ 128 / 8, "AES");
            byte[] encrypted;
            try {
                Cipher c = Secret.getCipher("AES/CFB8/NoPadding");
                c.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
                encrypted = c.doFinal(baos.toByteArray());
            } catch (GeneralSecurityException x) {
                throw new IOException(x);
            }
            res.setContentType("application/octet-stream");
            res.getOutputStream().write(iv);
            res.getOutputStream().write(encrypted);
        } else {
            it.checkPermission(connectPermission);
            view.forward(req, res);
        }
    }
}
