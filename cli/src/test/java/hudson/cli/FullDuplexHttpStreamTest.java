package hudson.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
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
        AtomicReference<String> capturedUserAgent = new AtomicReference<>();
        CountDownLatch requestCaptured = new CountDownLatch(1);

        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        HttpServer server = HttpServer.create(address, 0);

        server.createContext("/", exchange -> {
            String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            if (userAgent != null) {
                capturedUserAgent.set(userAgent);
                requestCaptured.countDown();
            }

            exchange.getResponseHeaders().add("X-Jenkins", "2.0");
            exchange.getResponseHeaders().add("X-Jenkins-CLI2", "third-generation");

            exchange.sendResponseHeaders(200, 0);

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

            boolean captured = requestCaptured.await(5, TimeUnit.SECONDS);
            streamThread.interrupt();

            assertTrue(captured, "Request should have reached the mock server");

            String expectedUserAgent = "Jenkins-cli-" + CLI.computeVersion();
            assertEquals(expectedUserAgent, capturedUserAgent.get(), "User-Agent header should match computed CLI version");
        } finally {
            server.stop(0);
        }
    }
}
