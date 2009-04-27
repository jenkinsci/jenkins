package hudson.cli;

import hudson.cli.SequenceOutputStream.Block;

import java.io.FilterOutputStream;
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
public class ChunkedHttpStreamPair {
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

    public ChunkedHttpStreamPair(URL target) throws IOException {
        this.target = target;

        // server->client side is very simple
        HttpURLConnection con = (HttpURLConnection) target.openConnection();
        con.setDoOutput(true); // request POST
        con.setRequestMethod("POST");
        con.addRequestProperty("Session",uuid.toString());
        con.addRequestProperty("Side","download");
        con.getOutputStream().close();
        input = con.getInputStream();

        // client->server needs to be chopped up into blocks since URLConnection
        // doesn't allow POST without Content-Length
//        output = new SequenceOutputStream(new HttpBlock(makeConnection())) {
//            protected Block next(Block current) throws IOException {
//                // wait for the server to finish the response
//                // before initiating the next connection. This guarantees
//                // that the uploaded data is handled in-order by the server
//                ((HttpBlock)current).close();
//
//                return new HttpBlock(makeConnection());
//            }
//        };

        // chunked encoding
        con = (HttpURLConnection) target.openConnection();
        con.setDoOutput(true); // request POST
        con.setRequestMethod("POST");
        con.setChunkedStreamingMode(0);
        con.addRequestProperty("Session",uuid.toString());
        con.addRequestProperty("Side","upload");
        output = con.getOutputStream();
    }

    private class HttpBlock extends Block {
        private final HttpURLConnection con;

        private HttpBlock(HttpURLConnection con) throws IOException {
            super(new FilterOutputStream(con.getOutputStream()) {
                public void write(byte[] b, int off, int len) throws IOException {
                    out.write(b, off, len);
                }
            }, BLOCK_SIZE);
            this.con = con;
        }

        void close() throws IOException {
            con.getInputStream().read();
        }
    }

    private HttpURLConnection makeConnection() throws IOException {
        HttpURLConnection con = (HttpURLConnection) target.openConnection();
        con.setDoOutput(true); // request POST
        con.setRequestMethod("POST");
        con.addRequestProperty("User-Agent","Hudson");
        con.addRequestProperty("Session",uuid.toString());
        con.addRequestProperty("Side","upload");
        con.setFixedLengthStreamingMode(BLOCK_SIZE);
        con.connect();

        return con;
    }

    static final int BLOCK_SIZE = 1024;
}
