package hudson.cli;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class HttpConnectionManager {

    private static final String DOWNLOAD_SIDE = "download";
    private static final String UPLOAD_SIDE = "upload";
    private static final String SESSION_HEADER = "Session";
    private static final String SIDE_HEADER = "Side";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String HUDSON_DUPLEX_HEADER = "Hudson-Duplex";
    private static final String CONTENT_TYPE_HEADER = "Content-type";

    private final String authorization;
    private final UUID uuid;

    public HttpConnectionManager(String authorization) {
        this.authorization = authorization;
        this.uuid = UUID.randomUUID();
    }

    public HttpURLConnection createConnection(URL target, String side) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) target.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.addRequestProperty(SESSION_HEADER, uuid.toString());
        connection.addRequestProperty(SIDE_HEADER, side);
        if (authorization != null) {
            connection.addRequestProperty(AUTHORIZATION_HEADER, authorization);
        }
        return connection;
    }

    public HttpURLConnection createDownloadConnection(URL target) throws IOException {
        HttpURLConnection connection = createConnection(target, DOWNLOAD_SIDE);
        connection.getOutputStream().close();  // Server expects a closed output stream for download
        return connection;
    }

    public HttpURLConnection createUploadConnection(URL target) throws IOException {
        HttpURLConnection connection = createConnection(target, UPLOAD_SIDE);
        connection.setChunkedStreamingMode(0);  // Unlimited data stream
        connection.setRequestProperty(CONTENT_TYPE_HEADER, "application/octet-stream");
        return connection;
    }
}
