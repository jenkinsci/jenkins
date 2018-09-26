package hudson.cli;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

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
    public void testJENKINS_URL() throws Exception {
        // Using -s option
        assertEquals(0, launch("java",
                               "-Duser.home=" + home,
                               "-jar", jar.getAbsolutePath(),
                               "-s", r.getURL().toString(),
                               "who-am-i")
        );

        // Without -s option and without JENKINS_URL
        assertNotEquals(0, launch("java",
                                  "-Duser.home=" + home,
                                  "-jar", jar.getAbsolutePath(),
                                  "who-am-i")
        );

        // Without -s option but with JENKINS_URL
        Map<String, String> envars = new HashMap<>();
        envars.put("JENKINS_URL", r.getURL().toString());
        assertEquals(0, launch(envars,
                               "java",
                               "-Duser.home=" + home,
                               "-jar", jar.getAbsolutePath(),
                               "who-am-i")
        );

        envars = new HashMap<>();
        envars.put("JENKINS_URL", "http://invalid-url");
        assertNotEquals(0, launch(envars,
                                  "java",
                                  "-Duser.home=" + home,
                                  "-jar", jar.getAbsolutePath(),
                                  "who-am-i")
        );

        // Override JENKINS_URL with -s option
        envars = new HashMap<>();
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
    public void testJENKINS_USER_IDandJENKINS_API_TOKEN() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));

        // Using -auth option
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            assertEquals(0, launch(Collections.emptyMap(), baos, null,
                                   "java",
                                   "-Duser.home=" + home,
                                   "-jar", jar.getAbsolutePath(),
                                   "-s", r.getURL().toString(),
                                   "-auth", "admin:admin",
                                   "who-am-i")
            );
            assertThat(baos.toString(), containsString("Authenticated as: admin"));
        }

        // Without -auth option and without env vars
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            assertEquals(0, launch(Collections.emptyMap(), baos, null,
                                   "java",
                                   "-Duser.home=" + home,
                                   "-jar", jar.getAbsolutePath(),
                                   "-s", r.getURL().toString(),
                                   "who-am-i")
            );
            assertThat(baos.toString(), containsString("Authenticated as: anonymous"));
        }

        // Without -s option but with both env var
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Map<String, String> envars = new HashMap<>();
            envars.put("JENKINS_USER_ID", "admin");
            envars.put("JENKINS_API_TOKEN", "admin");
            assertEquals(0, launch(envars, baos, null,
                                   "java",
                                   "-Duser.home=" + home,
                                   "-jar", jar.getAbsolutePath(),
                                   "-s", r.getURL().toString(),
                                   "who-am-i")
            );
            assertThat(baos.toString(), containsString("Authenticated as: admin"));
        }

        // Without -s option but with only one of the env var
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

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Map<String, String> envars = new HashMap<>();
            envars.put("JENKINS_API_TOKEN", "admin");
            assertNotEquals(0, launch(envars,
                                      "java",
                                      "-Duser.home=" + home,
                                      "-jar", jar.getAbsolutePath(),
                                      "-s", r.getURL().toString(),
                                      "who-am-i")
            );
        }

        // Override env vars with -auth option
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Map<String, String> envars = new HashMap<>();
            envars.put("JENKINS_USER_ID", "other-user");
            envars.put("JENKINS_API_TOKEN", "other-user");
            assertEquals(0, launch(envars, baos, null,
                                   "java",
                                   "-Duser.home=" + home,
                                   "-jar", jar.getAbsolutePath(),
                                   "-s", r.getURL().toString(),
                                   "-auth", "admin:admin",
                                   "who-am-i")
            );
            assertThat(baos.toString(), containsString("Authenticated as: admin"));
        }
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
