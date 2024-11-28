package hudson.cli;

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
 */
public class FullDuplexHttpStream {
    private final URL base;
    private final OutputStream output;
    private final InputStream input;

    private static final Logger LOGGER = Logger.getLogger(FullDuplexHttpStream.class.getName());

    public FullDuplexHttpStream(URL base, String relativeTarget, String authorization) throws IOException {
        if (!base.toString().endsWith("/")) {
            throw new IllegalArgumentException(base.toString());
        }
        if (relativeTarget.startsWith("/")) {
            throw new IllegalArgumentException(relativeTarget);
        }

        this.base = tryToResolveRedirects(base, authorization);
        URL target = new URL(this.base, relativeTarget);

        HttpConnectionManager connectionManager = new HttpConnectionManager(authorization);

        // Establish download side connection
        LOGGER.fine("establishing download side");
        HttpURLConnection con = connectionManager.createDownloadConnection(target);
        input = con.getInputStream();

        if (con.getHeaderField("Hudson-Duplex") == null) {
            throw new CLI.NotTalkingToJenkinsException("No Jenkins running at " + target + ", or not serving HTTP Duplex");
        }
        LOGGER.fine("established download side");

        // Establish upload side connection
        LOGGER.fine("establishing upload side");
        con = connectionManager.createUploadConnection(target);
        output = con.getOutputStream();
        LOGGER.fine("established upload side");
    }

    private URL tryToResolveRedirects(URL base, String authorization) {
        try {
            HttpURLConnection con = new HttpConnectionManager(authorization).createDownloadConnection(base);
            con.getInputStream().close();
            base = con.getURL();
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Failed to resolve potential redirects", ex);
        }
        return base;
    }

    public InputStream getInputStream() {
        return input;
    }

    public OutputStream getOutputStream() {
        return output;
    }
}
