package hudson.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
 *
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

    public InputStream getInputStream() {
        return input;
    }

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

        this.base = base;
        this.authorization = authorization;

        URL target = new URL(base, relativeTarget);

        CrumbData crumbData = new CrumbData();

        UUID uuid = UUID.randomUUID(); // so that the server can correlate those two connections

        // server->client
        HttpURLConnection con = (HttpURLConnection) target.openConnection();
        con.setDoOutput(true); // request POST to avoid caching
        con.setRequestMethod("POST");
        con.addRequestProperty("Session", uuid.toString());
        con.addRequestProperty("Side","download");
        if (authorization != null) {
            con.addRequestProperty("Authorization", authorization);
        }
        if(crumbData.isValid) {
            con.addRequestProperty(crumbData.crumbName, crumbData.crumb);
        }
        con.getOutputStream().close();
        input = con.getInputStream();
        // make sure we hit the right URL
        if (con.getHeaderField("Hudson-Duplex") == null) {
            throw new IOException(target + " does not look like Jenkins, or is not serving the HTTP Duplex transport");
        }

        // client->server uses chunked encoded POST for unlimited capacity. 
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

        if(crumbData.isValid) {
            con.addRequestProperty(crumbData.crumbName, crumbData.crumb);
        }
        output = con.getOutputStream();
    }

    static final int BLOCK_SIZE = 1024;
    static final Logger LOGGER = Logger.getLogger(FullDuplexHttpStream.class.getName());
    
    private final class CrumbData {
    	String crumbName;
    	String crumb;
    	boolean isValid;

    	private CrumbData() {
            this.crumbName = "";
            this.crumb = "";
            this.isValid = false;
            getData();
    	}

    	private void getData() {
            try {
                String base = createCrumbUrlBase();
                String[] pair = readData(base + "?xpath=concat(//crumbRequestField,\":\",//crumb)").split(":", 2);
                crumbName = pair[0];
                crumb = pair[1];
                isValid = true;
                LOGGER.fine("Crumb data: "+crumbName+"="+crumb);
            } catch (IOException e) {
                // presumably this Hudson doesn't use crumb 
                LOGGER.log(Level.FINE,"Failed to get crumb data",e);
            }
    	}

    	private String createCrumbUrlBase() {
            return base + "crumbIssuer/api/xml/";
    	}

    	private String readData(String dest) throws IOException {
            HttpURLConnection con = (HttpURLConnection) new URL(dest).openConnection();
            if (authorization != null) {
                con.addRequestProperty("Authorization", authorization);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String line = reader.readLine();
                String nextLine = reader.readLine();
                if (nextLine != null) {
                    System.err.println("Warning: received junk from " + dest);
                    System.err.println(line);
                    System.err.println(nextLine);
                    while ((nextLine = reader.readLine()) != null) {
                        System.err.println(nextLine);
                    }
                }
                return line;
            }
            finally {
                con.disconnect();
            }
        }
    }
}
