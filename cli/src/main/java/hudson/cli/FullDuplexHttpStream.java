package hudson.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

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

        CrumbData crumbData = new CrumbData();
        
        // server->client
        HttpURLConnection con = (HttpURLConnection) target.openConnection();
        con.setDoOutput(true); // request POST to avoid caching
        con.setRequestMethod("POST");
        con.addRequestProperty("Session",uuid.toString());
        con.addRequestProperty("Side","download");
        if(crumbData.isValid) {
            con.addRequestProperty(crumbData.crumbName, crumbData.crumb);
        }
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
                crumbName = readData(base+"?xpath=/*/crumbRequestField/text()");
                crumb = readData(base+"?xpath=/*/crumb/text()");
                isValid = true;
                LOGGER.fine("Crumb data: "+crumbName+"="+crumb);
            } catch (IOException e) {
                // presumably this Hudson doesn't use crumb 
                LOGGER.log(Level.FINE,"Failed to get crumb data",e);
            }
    	}

    	private String createCrumbUrlBase() {
            String url = target.toExternalForm();    		
            return new StringBuilder(url.substring(0, url.lastIndexOf("/cli"))).append("/crumbIssuer/api/xml/").toString();
    	}

    	private String readData(String dest) throws IOException {
            HttpURLConnection con = (HttpURLConnection) new URL(dest).openConnection();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
                return reader.readLine();
            }
            finally {
                con.disconnect();
            }
        }
    }
}
