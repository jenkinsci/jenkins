package hudson.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

/**
 * Creates a capacity-unlimited bi-directional {@link InputStream}/{@link OutputStream} pair over
 * HTTP, which is a request/response protocol.
 *
 * @author Kohsuke Kawaguchi
 */
public class FullDuplexHttpStream {
    private final URL target;
    /**
     * Uniquely identifies this connection, so that the server can bundle separate HTTP requests together.
     */
    private final UUID uuid = UUID.randomUUID();

    private final OutputStream output;
    private final InputStream input;

    public InputStream getInputStream() {
        return input;
    }

    public OutputStream getOutputStream() {
        return output;
    }

    public FullDuplexHttpStream(URL target) throws IOException {
        this.target = target;

        // server->client
        HttpURLConnection con = (HttpURLConnection) target.openConnection();
        con.setDoOutput(true); // request POST to avoid caching
        con.setRequestMethod("POST");
        con.addRequestProperty("Session",uuid.toString());
        con.addRequestProperty("Side","download");
        con.getOutputStream().close();
        input = con.getInputStream();
        // make sure we hit the right URL
        if(con.getHeaderField("Hudson-Duplex")==null)
            throw new IOException(target+" doesn't look like Hudson");

        // client->server uses chunked encoded POST for unlimited capacity. 
        con = (HttpURLConnection) target.openConnection();
        con.setDoOutput(true); // request POST
        con.setRequestMethod("POST");
        con.setChunkedStreamingMode(0);
        con.addRequestProperty("Session",uuid.toString());
        con.addRequestProperty("Side","upload");
        output = con.getOutputStream();
    }

    static final int BLOCK_SIZE = 1024;
}
