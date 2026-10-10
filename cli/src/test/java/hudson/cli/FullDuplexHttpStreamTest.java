package hudson.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class FullDuplexHttpStreamTest {

    @Test
    public void testFullDuplexHttpStreamSetsUserAgentHeader() throws Exception {
        AtomicReference<String> redirectUserAgent = new AtomicReference<>();
        AtomicReference<String> downloadUserAgent = new AtomicReference<>();
        AtomicReference<String> uploadUserAgent = new AtomicReference<>();

        CountDownLatch redirectCaptured = new CountDownLatch(1);
        CountDownLatch downloadCaptured = new CountDownLatch(1);
        CountDownLatch uploadCaptured = new CountDownLatch(1);

        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        HttpServer server = HttpServer.create(address, 0);

        server.createContext("/", exchange -> {
            String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            String side = exchange.getRequestHeaders().getFirst("Side");

            if (side == null) {
                if (userAgent != null) {
                    redirectUserAgent.set(userAgent);
                    redirectCaptured.countDown();
                }
                exchange.getResponseHeaders().add("X-Jenkins", "2.0");
                exchange.getResponseHeaders().add("X-Jenkins-CLI2", "third-generation");
                exchange.sendResponseHeaders(200, 0);
            } else if ("download".equals(side)) {
                if (userAgent != null) {
                    downloadUserAgent.set(userAgent);
                    downloadCaptured.countDown();
                }
                exchange.getResponseHeaders().add("Hudson-Duplex", "true");
                exchange.sendResponseHeaders(200, 0);
            } else if ("upload".equals(side)) {
                if (userAgent != null) {
                    uploadUserAgent.set(userAgent);
                    uploadCaptured.countDown();
                }
                exchange.sendResponseHeaders(200, 0);
            }

            // Close response body to finish the chunked HTTP response and let client proceed
            try (OutputStream os = exchange.getResponseBody()) {
                // no-op, just closing finishes the chunked transfer
            } catch (Exception ignored) {
            }

            try (InputStream is = exchange.getRequestBody()) {
                byte[] buf = new byte[1024];
                while (is.read(buf) != -1) {
                    // Drain request body
                }
            } catch (Exception ignored) {
                // Ignore stream aborts from test teardown
            }
        });

        server.start();

        try {
            int port = server.getAddress().getPort();
            URL testUrl = new URL("http://127.0.0.1:" + port + "/");

            Thread streamThread = new Thread(() -> {
                try {
                    new FullDuplexHttpStream(testUrl, "", null);
                } catch (Exception ignored) {
                    // Stream initialization interrupted intentionally
                }
            });
            streamThread.start();

            boolean redirectOk = redirectCaptured.await(5, TimeUnit.SECONDS);
            boolean downloadOk = downloadCaptured.await(5, TimeUnit.SECONDS);
            boolean uploadOk = uploadCaptured.await(5, TimeUnit.SECONDS);

            streamThread.interrupt();

            assertTrue(redirectOk, "Redirect request should have reached the mock server");
            assertTrue(downloadOk, "Download request should have reached the mock server");
            assertTrue(uploadOk, "Upload request should have reached the mock server");

            String expectedUserAgent = "Jenkins-cli-" + CLI.computeVersion();
            assertEquals(expectedUserAgent, redirectUserAgent.get(), "Redirect User-Agent header should match computed CLI version");
            assertEquals(expectedUserAgent, downloadUserAgent.get(), "Download User-Agent header should match computed CLI version");
            assertEquals(expectedUserAgent, uploadUserAgent.get(), "Upload User-Agent header should match computed CLI version");
        } finally {
            server.stop(0);
        }
    }
}
