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
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.jvnet.hudson.test.LoggerRule.recorded;

import hudson.Functions;
import hudson.init.impl.InstallUncaughtExceptionHandler;
import java.io.File;
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
import org.apache.commons.io.FileUtils;
import org.awaitility.Awaitility;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.commons.util.ExceptionUtils;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class Security3630Test {
    public static final int CONCURRENCY = 50;
    public static final int ITERATIONS = 50;
    private JenkinsRule j = new JenkinsRule();

    @TempDir
    private File tmp;

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
    void testHashMap() throws InterruptedException, IOException {
        // https://github.com/jenkins-infra/helpdesk/issues/4904
        assumeFalse(Functions.isWindows());
        // If this test appears flaky, it's probably not: The race condition cannot be reliably triggered.
        // If the assertion fails, then there's probably a bug here.
        // TODO Do we want to keep a test like this?
        loggerRule.capture(100);
        final File jar = File.createTempFile("jenkins-cli.jar", null, tmp);
        FileUtils.copyURLToFile(j.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
        for (int i = 0; i < ITERATIONS; i++) {
            List<Callable<Void>> callables = new ArrayList<>();
            for (int c = 0; c < CONCURRENCY; c++) {
                callables.add(() -> {
                    new ProcessBuilder().command("java", "-jar", jar.getAbsolutePath(), "-http", "-s", j.getURL().toString(), "who-am-i").start().waitFor();
                    return null;
                });
            }

            ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
            List<Future<Void>> futures = executor.invokeAll(callables);

            futures.forEach(f -> {
                try {
                    f.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
            assertThat(loggerRule.getRecords().stream().map(r -> String.join("\n", r.getMessage(), r.getLoggerName(), r.getThrown().getMessage(), ExceptionUtils.readStackTrace(r.getThrown()))).collect(Collectors.toList()), empty());
            // See #control() assertion for the "expected" failure without the fix.
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
