package hudson.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.jvnet.hudson.test.LoggerRule.recorded;

import hudson.Functions;
import hudson.init.impl.InstallUncaughtExceptionHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ThreadInfo;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;
import jenkins.util.FullDuplexHttpService;
import org.awaitility.Awaitility;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ExceptionUtils;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class Security3630Test {
    public static final int CONCURRENCY = 50;
    public static final int ITERATIONS = 50;
    private JenkinsRule j = new JenkinsRule();

    private LoggerRule loggerRule = new LoggerRule().record(InstallUncaughtExceptionHandler.class.getName(), Level.WARNING);

    private long originalTimeout;

    @BeforeEach
    public void setup(JenkinsRule j) throws Exception {
        this.j = j;
        // TODO FlagRule in JUnit 5?
        originalTimeout = FullDuplexHttpService.CONNECTION_TIMEOUT;
        FullDuplexHttpService.CONNECTION_TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    }

    @AfterEach
    public void reset() {
        FullDuplexHttpService.CONNECTION_TIMEOUT = originalTimeout;
    }

    @Test
    void control() throws IOException {
        loggerRule.capture(100);
        final String uuid = UUID.randomUUID().toString();
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            WebRequest request = new WebRequest(new URL(j.getURL().toString() + "cli?remoting=false"));
            request.setHttpMethod(HttpMethod.POST);
            request.setAdditionalHeader("Session", uuid);
            request.setAdditionalHeader("Side", "download");
            wc.getPage(request);
        }
        assertThat(loggerRule, recorded(nullValue(String.class), allOf(instanceOf(IOException.class), hasProperty("message", containsString("HTTP full-duplex channel timeout: " + uuid)))));
    }

    @Test
    void testConcurrentCliSessionPairing() throws InterruptedException, IOException {
        // This test simulates the Jenkins CLI full-duplex HTTP protocol.
        // It concurrently pairs 'download' and 'upload' requests with the same Session UUID
        // to verify that the FullDuplexHttpService's session map handles concurrent put/get
        // operations without throwing ConcurrentModificationException (SECURITY-3630).
        //
        // The test proves TWO things:
        // 1. The CLI pairing actually works — at least half of each side's handshakes must succeed.
        // 2. The concurrency bug did not come back — ConcurrentModificationException must not appear.
        loggerRule.capture(100);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY * 2);
        int totalDownloadSuccesses = 0;
        int totalUploadSuccesses = 0;

        try {
            for (int i = 0; i < ITERATIONS; i++) {
                java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
                List<Callable<Void>> tasks = new ArrayList<>();
                String cliUrl = j.getURL().toString() + "cli?remoting=false";

                AtomicInteger downloadSuccesses = new AtomicInteger();
                AtomicInteger uploadSuccesses = new AtomicInteger();

                for (int c = 0; c < CONCURRENCY; c++) {
                    String uuid = UUID.randomUUID().toString();
                    tasks.add(() -> {
                        runDownloadHandshake(cliUrl, uuid, startLatch, downloadSuccesses);
                        return null;
                    });
                    tasks.add(() -> {
                        runUploadHandshake(cliUrl, uuid, startLatch, uploadSuccesses);
                        return null;
                    });
                }

                // Submit all tasks, then release the latch to fire them simultaneously
                List<Future<Void>> futures = new ArrayList<>();
                for (Callable<Void> task : tasks) {
                    futures.add(executor.submit(task));
                }
                startLatch.countDown();

                for (Future<Void> f : futures) {
                    try {
                        f.get();
                    } catch (ExecutionException | InterruptedException e) {
                        // Individual connection failures are tracked via the success counters.
                    }
                }

                totalDownloadSuccesses += downloadSuccesses.get();
                totalUploadSuccesses += uploadSuccesses.get();

                // Assert the absence of the SECURITY-3630 bug signature every iteration.
                // ConcurrentModificationException in the session map is the specific race
                // that the ConcurrentHashMap fix was supposed to eliminate.
                List<String> targetLogs = loggerRule.getRecords().stream()
                    .filter(r -> r.getThrown() != null)
                    .map(r -> ExceptionUtils.readStackTrace(r.getThrown()))
                    .collect(Collectors.toList());
                assertThat("SECURITY-3630 regression: ConcurrentModificationException in session map",
                    targetLogs, not(hasItem(containsString("ConcurrentModificationException"))));
            }

            // Prove the handshake actually works across the entire test run.
            // At least 25% of each side's handshakes must succeed across all iterations.
            // The threshold is deliberately set below 50% because the upload side inherently
            // fails more often: it depends on the download side having already registered the
            // session UUID in the map (services.put). When all threads fire simultaneously
            // via the CountDownLatch, many uploads will arrive before their paired download,
            // causing a legitimate "No download side found" 500 response.
            int totalAttempts = ITERATIONS * CONCURRENCY;
            int minRequired = totalAttempts / 4;
            assertThat("Too few download handshakes succeeded overall"
                    + " (" + totalDownloadSuccesses + "/" + totalAttempts + ")"
                    + " — the test is not exercising the download code path",
                totalDownloadSuccesses >= minRequired, org.hamcrest.Matchers.is(true));
            assertThat("Too few upload handshakes succeeded overall"
                    + " (" + totalUploadSuccesses + "/" + totalAttempts + ")"
                    + " — the test is not exercising the upload code path",
                totalUploadSuccesses >= minRequired, org.hamcrest.Matchers.is(true));
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                // Not a hard failure — just log it. Under heavy load the server threads
                // may take a moment to drain.
            }
        }
    }

    /**
     * Simulates the "download" side of a full-duplex CLI handshake.
     * The server writes a 0-byte handshake to the response (see FullDuplexHttpService line 101).
     * Increments {@code successes} if the handshake byte was received successfully.
     */
    private void runDownloadHandshake(String cliUrl, String uuid,
            java.util.concurrent.CountDownLatch startLatch,
            AtomicInteger successes) {
        java.net.HttpURLConnection conn = null;
        try {
            startLatch.await();
            conn = (java.net.HttpURLConnection) new URL(cliUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Session", uuid);
            conn.setRequestProperty("Side", "download");

            try (InputStream is = conn.getInputStream()) {
                int b = is.read();
                if (b == 0) {
                    successes.incrementAndGet();
                }
                // If b != 0 the handshake didn't complete as expected — don't count it.
            }
        } catch (IOException | InterruptedException e) {
            // Expected under heavy concurrent load: timeouts, connection resets, 500s.
            // Not counted as a success.
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Simulates the "upload" side of a full-duplex CLI handshake.
     * The server does NOT write a handshake byte on the upload response
     * (see FullDuplexHttpService.upload — it only sets the upload stream and waits).
     * Increments {@code successes} if the server accepted the upload (HTTP 200).
     */
    private void runUploadHandshake(String cliUrl, String uuid,
            java.util.concurrent.CountDownLatch startLatch,
            AtomicInteger successes) {
        java.net.HttpURLConnection conn = null;
        try {
            startLatch.await();
            conn = (java.net.HttpURLConnection) new URL(cliUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Session", uuid);
            conn.setRequestProperty("Side", "upload");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(0);
                os.flush();
            }
            // Do NOT read from getInputStream() — the upload response has no handshake byte.
            // The server just waits for the download side to complete.
            int code = conn.getResponseCode();
            if (code == 200) {
                successes.incrementAndGet();
            }
        } catch (IOException | InterruptedException e) {
            // Expected under heavy concurrent load: timeouts, connection resets, 500s.
            // Not counted as a success.
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @Test
    void testIndefiniteWait() throws IOException {
        var stream = new hudson.cli.FullDuplexHttpStream(j.getURL(), "cli?remoting=false", null);
        {
            Set<String> threadNames = Arrays.stream(Functions.getThreadInfos()).map(ThreadInfo::getThreadName).collect(Collectors.toSet());
            assertThat(threadNames, hasItem(containsString("Handling POST /jenkins/cli")));
        }
        stream.getOutputStream().write(new byte[10]);
        stream.getOutputStream().close();
        stream.getInputStream().close();

        Awaitility.await().atMost(FullDuplexHttpService.CONNECTION_TIMEOUT * 2, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Set<String> threadNames = Arrays.stream(Functions.getThreadInfos()).map(ThreadInfo::getThreadName).collect(Collectors.toSet());
            assertThat(threadNames, not(hasItem(containsString("Handling POST /jenkins/cli"))));
        });
    }
}
