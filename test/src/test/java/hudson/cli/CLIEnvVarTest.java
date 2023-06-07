package hudson.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.User;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import jenkins.security.apitoken.ApiTokenPropertyConfiguration;
import org.apache.commons.io.FileUtils;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class CLIEnvVarTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File home;
    private File jar;

    @Before
    public void grabCliJar() throws IOException {
        home = tmp.newFolder();
        jar = tmp.newFile("jenkins-cli.jar");
        FileUtils.copyURLToFile(r.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
    }

    @Test
    public void testSOptionWithoutJENKINS_URL() throws Exception {
        assertEquals(0, launch("java",
                "-Duser.home=" + home,
                "-jar", jar.getAbsolutePath(),
                "-s", r.getURL().toString(),
                "who-am-i")
        );
    }

    @Test
    public void testWithoutSOptionAndWithoutJENKINS_URL() throws Exception {
        Assume.assumeThat(System.getenv("JENKINS_URL"), is(nullValue())); // TODO instead remove it from the process env?
        assertNotEquals(0, launch("java",
                "-Duser.home=" + home,
                "-jar", jar.getAbsolutePath(),
                "who-am-i")
        );
    }

    @Test
    public void testJENKINS_URLWithoutSOption() throws Exception {
        // Valid URL
        Map<String, String> envars = new HashMap<>();
        envars.put("JENKINS_URL", r.getURL().toString());
        assertEquals(0, launch(envars,
                "java",
                "-Duser.home=" + home,
                "-jar", jar.getAbsolutePath(),
                "who-am-i")
        );

        // Invalid URL
        envars = new HashMap<>();
        envars.put("JENKINS_URL", "http://invalid-url");
        assertNotEquals(0, launch(envars,
                "java",
                "-Duser.home=" + home,
                "-jar", jar.getAbsolutePath(),
                "who-am-i")
        );

    }

    @Test
    public void testSOptionOverridesJENKINS_URL() throws Exception {
        Map<String, String> envars = new HashMap<>();
        envars.put("JENKINS_URL", "http://invalid-url");
        assertEquals(0, launch(envars,
                "java",
                "-Duser.home=" + home,
                "-jar", jar.getAbsolutePath(),
                "-s", r.getURL().toString(),
                "who-am-i")
        );
    }

    @Test
    public void testAuthOptionWithoutEnvVars() throws Exception {
        String token = getToken();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            assertEquals(0, launch(Collections.emptyMap(), baos, null,
                    "java",
                    "-Duser.home=" + home,
                    "-jar", jar.getAbsolutePath(),
                    "-s", r.getURL().toString(),
                    "-auth", String.format("%s:%s", "admin", token),
                    "who-am-i")
            );
            assertThat(baos.toString(Charset.defaultCharset()), containsString("Authenticated as: admin"));
        }
    }

    @Test
    public void testWithoutEnvVarsAndWithoutAuthOption() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            assertEquals(0, launch(Collections.emptyMap(), baos, null,
                    "java",
                    "-Duser.home=" + home,
                    "-jar", jar.getAbsolutePath(),
                    "-s", r.getURL().toString(),
                    "who-am-i")
            );
            assertThat(baos.toString(Charset.defaultCharset()), containsString("Authenticated as: anonymous"));
        }
    }

    @Test
    public void testEnvVarsWithoutAuthOption() throws Exception {
        String token = getToken();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Map<String, String> envars = new HashMap<>();
            envars.put("JENKINS_USER_ID", "admin");
            envars.put("JENKINS_API_TOKEN", token);
            assertEquals(0, launch(envars, baos, null,
                    "java",
                    "-Duser.home=" + home,
                    "-jar", jar.getAbsolutePath(),
                    "-s", r.getURL().toString(),
                    "who-am-i")
            );
            assertThat(baos.toString(Charset.defaultCharset()), containsString("Authenticated as: admin"));
        }
    }

    @Test
    public void testOnlyOneEnvVar() throws Exception {
        String token = getToken();

        // only JENKINS_USER_ID
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Map<String, String> envars = new HashMap<>();
            envars.put("JENKINS_USER_ID", "admin");
            assertNotEquals(0, launch(envars,
                                      "java",
                                      "-Duser.home=" + home,
                                      "-jar", jar.getAbsolutePath(),
                                      "-s", r.getURL().toString(),
                                      "who-am-i")
            );
        }

        // only JENKINS_API_TOKEN
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Map<String, String> envars = new HashMap<>();
            envars.put("JENKINS_API_TOKEN", token);
            assertNotEquals(0, launch(envars,
                                      "java",
                                      "-Duser.home=" + home,
                                      "-jar", jar.getAbsolutePath(),
                                      "-s", r.getURL().toString(),
                                      "who-am-i")
            );
        }
    }

    @Test
    public void testAuthOptionOverridesEnvVars() throws Exception {
        String token = getToken();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Map<String, String> envars = new HashMap<>();
            envars.put("JENKINS_USER_ID", "other-user");
            envars.put("JENKINS_API_TOKEN", "other-user");
            assertEquals(0, launch(envars, baos, null,
                    "java",
                    "-Duser.home=" + home,
                    "-jar", jar.getAbsolutePath(),
                    "-s", r.getURL().toString(),
                    "-auth", String.format("%s:%s", "admin", token),
                    "who-am-i")
            );
            assertThat(baos.toString(Charset.defaultCharset()), containsString("Authenticated as: admin"));
        }
    }

    private String getToken() {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setTokenGenerationOnCreationEnabled(true);

        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));

        return User.getOrCreateByIdOrFullName("admin").getProperty(ApiTokenProperty.class).getApiToken();
    }

    private int launch(String... cmdArgs) throws Exception {
        return launch(Collections.emptyMap(), cmdArgs);
    }

    private int launch(Map<String, String> envars, String... cmdArgs) throws Exception {
        return launch(envars, null, null, cmdArgs);
    }

    private int launch(Map<String, String> envars, OutputStream out, OutputStream err, String... cmdArgs) throws Exception {
        if (out == null) {
            out = System.out;
        }

        if (err == null) {
            err = System.err;
        }

        return new Launcher.LocalLauncher(StreamTaskListener.fromStderr())
                .decorateByEnv(new EnvVars(envars))
                .launch()
                .cmds(cmdArgs)
                .stdout(out)
                .stderr(err)
                .join();
    }
}
