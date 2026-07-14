package hudson.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.jvnet.hudson.test.LoggerRule.recorded;

import hudson.Functions;
import hudson.init.impl.InstallUncaughtExceptionHandler;
import java.io.IOException;
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
        // This test simulates the Jenkins CLI full-duplex HTTP protocol natively using CLI._main.
        // It concurrently establishes 'download' and 'upload' connections
        // to verify that the FullDuplexHttpService's session map handles concurrent put/get
        // operations without throwing ConcurrentModificationException (SECURITY-3630).
        loggerRule.capture(CONCURRENCY * ITERATIONS + 10);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);

        try {
            for (int i = 0; i < ITERATIONS; i++) {
                java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
                List<Callable<Void>> tasks = new ArrayList<>();
                URL baseUrl = j.getURL();

                for (int c = 0; c < CONCURRENCY; c++) {
                    tasks.add(() -> {
                        startLatch.await();
                        try {
                            hudson.cli.CLI._main(new String[] {"-http", "-s", baseUrl.toString(), "who-am-i"});
                        } catch (Exception e) {
                            // Expected under heavy concurrent load: timeouts, connection resets, 500s.
                        }
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
                        f.get(FullDuplexHttpService.CONNECTION_TIMEOUT * 2, TimeUnit.MILLISECONDS);
                    } catch (java.util.concurrent.TimeoutException e) {
                        f.cancel(true);
                    } catch (ExecutionException e) {
                        // Expected
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }

                // Assert the absence of the SECURITY-3630 bug signature every iteration.
                // The original issue resulted in ConcurrentModificationException (or corrupted map states
                // resulting in NullPointerException/IOException) being logged by the Jenkins server.
                List<String> targetLogs = loggerRule.getRecords().stream()
                    .filter(r -> r.getThrown() != null)
                    .filter(r -> {
                        Throwable t = r.getThrown();
                        while (t != null) {
                            if (t instanceof InterruptedException) return false;
                            t = t.getCause();
                        }
                        return true;
                    })
                    .map(r -> String.join("\n",
                            String.valueOf(r.getMessage()),
                            String.valueOf(r.getLoggerName()),
                            String.valueOf(r.getThrown().getMessage()),
                            ExceptionUtils.readStackTrace(r.getThrown())))
                    .collect(Collectors.toList());
                assertThat("SECURITY-3630 regression: Uncaught exceptions logged in session map (likely ConcurrentModificationException or IOException)",
                    targetLogs, empty());
            }
        } finally {
            executor.shutdownNow();
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
