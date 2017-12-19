package hudson.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Base64;

/**
 * Creates a capacity-unlimited bi-directional {@link InputStream}/{@link OutputStream} pair over
 * HTTP, which is a request/response protocol.
 * {@code FullDuplexHttpService} is the counterpart on the server side.
 * @author Kohsuke Kawaguchi
 */
public class FullDuplexHttpStream {
    private final URL base;
    /**
     * Authorization header value needed to get through the HTTP layer.
     */
    private final String authorization;
    
    private final OutputStream output;
    private final InputStream input;

    /**
     * A way to get data from the server.
     * There will be an initial zero byte used as a handshake which you should expect and ignore.
     */
    public InputStream getInputStream() {
        return input;
    }

    /**
     * A way to upload data to the server.
     * You will need to write to this and {@link OutputStream#flush} it to finish establishing a connection.
     */
    public OutputStream getOutputStream() {
        return output;
    }

    @Deprecated
    public FullDuplexHttpStream(URL target) throws IOException {
        this(target,basicAuth(target.getUserInfo()));
    }

    private static String basicAuth(String userInfo) {
        if (userInfo != null)
            return "Basic "+new String(Base64.encodeBase64(userInfo.getBytes()));
        return null;
    }

    /**
     * @param target something like {@code http://jenkins/cli?remoting=true}
     *               which we then need to split into {@code http://jenkins/} + {@code cli?remoting=true}
     *               in order to construct a crumb issuer request
     * @deprecated use {@link #FullDuplexHttpStream(URL, String, String)} instead
     */
    @Deprecated
    public FullDuplexHttpStream(URL target, String authorization) throws IOException {
        this(new URL(target.toString().replaceFirst("/cli.*$", "/")), target.toString().replaceFirst("^.+/(cli.*)$", "$1"), authorization);
    }

    /**
     * @param base the base URL of Jenkins
     * @param relativeTarget
     *      The endpoint that we are making requests to.
     * @param authorization
     *      The value of the authorization header, if non-null.
     */
    public FullDuplexHttpStream(URL base, String relativeTarget, String authorization) throws IOException {
        if (!base.toString().endsWith("/")) {
            throw new IllegalArgumentException(base.toString());
        }
        if (relativeTarget.startsWith("/")) {
            throw new IllegalArgumentException(relativeTarget);
        }

        this.base = tryToResolveRedirects(base, authorization);
        this.authorization = authorization;

        URL target = new URL(this.base, relativeTarget);

        UUID uuid = UUID.randomUUID(); // so that the server can correlate those two connections

        // server->client
        LOGGER.fine("establishing download side");
        HttpURLConnection con = (HttpURLConnection) target.openConnection();
        con.setDoOutput(true); // request POST to avoid caching
        con.setRequestMethod("POST");
        con.addRequestProperty("Session", uuid.toString());
        con.addRequestProperty("Side","download");
        if (authorization != null) {
            con.addRequestProperty("Authorization", authorization);
        }
        con.getOutputStream().close();
        input = con.getInputStream();
        // make sure we hit the right URL; no need for CLI.verifyJenkinsConnection here
        if (con.getHeaderField("Hudson-Duplex") == null) {
            throw new CLI.NotTalkingToJenkinsException("There's no Jenkins running at " + target + ", or is not serving the HTTP Duplex transport");
        }
        LOGGER.fine("established download side"); // calling getResponseCode or getHeaderFields breaks everything

        // client->server uses chunked encoded POST for unlimited capacity.
        LOGGER.fine("establishing upload side");
        con = (HttpURLConnection) target.openConnection();
        con.setDoOutput(true); // request POST
        con.setRequestMethod("POST");
        con.setChunkedStreamingMode(0);
        con.setRequestProperty("Content-type","application/octet-stream");
        con.addRequestProperty("Session", uuid.toString());
        con.addRequestProperty("Side","upload");
        if (authorization != null) {
        	con.addRequestProperty ("Authorization", authorization);
        }
        output = con.getOutputStream();
        LOGGER.fine("established upload side");
    }

    // As this transport mode is using POST, it is necessary to resolve possible redirections using GET first.
    private URL tryToResolveRedirects(URL base, String authorization) {
        try {
            HttpURLConnection con = (HttpURLConnection) base.openConnection();
            if (authorization != null) {
                con.addRequestProperty("Authorization", authorization);
            }
            con.getInputStream().close();
            base = con.getURL();
        } catch (Exception ex) {
            // Do not obscure the problem propagating the exception. If the problem is real it will manifest during the
            // actual exchange so will be reported properly there. If it is not real (no permission in UI but sufficient
            // for CLI connection using one of its mechanisms), there is no reason to bother user about it.
            LOGGER.log(Level.FINE, "Failed to resolve potential redirects", ex);
        }
        return base;
    }

    static final int BLOCK_SIZE = 1024;
    static final Logger LOGGER = Logger.getLogger(FullDuplexHttpStream.class.getName());
    
}
