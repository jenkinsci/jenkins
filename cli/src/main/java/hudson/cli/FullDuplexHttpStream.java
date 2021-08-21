package hudson.cli;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates a capacity-unlimited bi-directional {@link InputStream}/{@link OutputStream} pair over
 * HTTP, which is a request/response protocol.
 * {@code FullDuplexHttpService} is the counterpart on the server side.
 * @author Kohsuke Kawaguchi
 */
public class FullDuplexHttpStream {
    private final URL base;
    
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

        URL target = new URL(this.base, relativeTarget);

        UUID uuid = UUID.randomUUID(); // so that the server can correlate those two connections

        // server->client
        LOGGER.fine("establishing download side");
        HttpURLConnection con = openHttpConnection(target);
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
        con = openHttpConnection(target);
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

    @SuppressFBWarnings(value = "URLCONNECTION_SSRF_FD", justification = "Client-side code doesn't involve SSRF.")
    private HttpURLConnection openHttpConnection(URL target) throws IOException {
        return (HttpURLConnection) target.openConnection();
    }

    // As this transport mode is using POST, it is necessary to resolve possible redirections using GET first.    @SuppressFBWarnings(value = "URLCONNECTION_SSRF_FD", justification = "Client-side code doesn't involve SSRF.")
    private URL tryToResolveRedirects(URL base, String authorization) {
        try {
            HttpURLConnection con = openHttpConnection(base);
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
