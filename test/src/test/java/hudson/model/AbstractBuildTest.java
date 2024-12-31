/*
 * The MIT License
 *
 * Copyright (c) 2004-2015, Sun Microsystems, Inc., CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import hudson.EnvVars;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.queue.QueueTaskFuture;
import hudson.agents.EnvironmentVariablesNodeProperty;
import hudson.agents.WorkspaceList;
import hudson.tasks.BatchFile;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.tasks.LogRotatorTest;
import hudson.tasks.Recorder;
import hudson.tasks.Shell;
import hudson.util.OneShotEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.htmlunit.Page;
import org.htmlunit.WebResponse;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.FakeChangeLogSCM;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.UnstableBuilder;
import org.xml.sax.SAXException;

/**
 * Unit tests of {@link AbstractBuild}.
 */
public class AbstractBuildTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule();

    @Test
    @Issue("JENKINS-30730")
    public void reportErrorShouldNotFailForNonPublisherClass() throws Exception {
        FreeStyleProject prj = j.createFreeStyleProject();
        ErroneousJobProperty erroneousJobProperty = new ErroneousJobProperty();
        prj.addProperty(erroneousJobProperty);
        QueueTaskFuture<FreeStyleBuild> future = prj.scheduleBuild2(0);
        assertThat("Build should be actually scheduled by Jenkins", future, notNullValue());
        FreeStyleBuild build = future.get();
        j.assertLogContains(ErroneousJobProperty.ERROR_MESSAGE, build);
        j.assertLogNotContains(ClassCastException.class.getName(), build);
    }

    /**
     * Job property, which always fails with an exception.
     */
    public static class ErroneousJobProperty extends JobProperty<FreeStyleProject> {

        public static final String ERROR_MESSAGE = "This publisher fails by design";

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException {
            throw new IOException(ERROR_MESSAGE);
        }

        @TestExtension("reportErrorShouldNotFailForNonPublisherClass")
        public static class DescriptorImpl extends JobPropertyDescriptor {}
    }

    @Test
    public void variablesResolved() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        j.jenkins.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("KEY1", "value"), new EnvironmentVariablesNodeProperty.Entry("KEY2", "$KEY1")));
        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        j.buildAndAssertSuccess(project);

        EnvVars envVars = builder.getEnvVars();
        assertEquals("value", envVars.get("KEY1"));
        assertEquals("value", envVars.get("KEY2"));
    }

    /**
     * Makes sure that raw console output doesn't get affected by XML escapes.
     */
    @Test
    public void rawConsoleOutput() throws Exception {
        final String out = "<test>&</test>";

        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
                listener.getLogger().println(out);
                return true;
            }
        });
        FreeStyleBuild b = j.buildAndAssertSuccess(p);
        Page rsp = j.createWebClient().goTo(b.getUrl() + "/consoleText", "text/plain");
        assertThat(rsp.getWebResponse().getContentAsString(), containsString(out));
    }

    private void assertCulprits(AbstractBuild<?, ?> b, String... expectedIds) throws IOException, SAXException {
        Set<String> actual = new TreeSet<>();
        for (User u : b.getCulprits()) {
            actual.add(u.getId());
        }
        assertEquals(actual, new TreeSet<>(Arrays.asList(expectedIds)));

        if (expectedIds.length > 0) {
            JenkinsRule.WebClient wc = j.createWebClient();
            WebResponse response = wc.goTo(b.getUrl() + "api/json?tree=culprits[id]", "application/json").getWebResponse();
            JSONObject json = JSONObject.fromObject(response.getContentAsString());

            Object culpritsArray = json.get("culprits");
            assertNotNull(culpritsArray);
            assertThat(culpritsArray, instanceOf(JSONArray.class));
            Set<String> fromApi = new TreeSet<>();
            for (Object o : ((JSONArray) culpritsArray).toArray()) {
                assertThat(o, instanceOf(JSONObject.class));
                Object id = ((JSONObject) o).get("id");
                if (id instanceof String) {
                    fromApi.add((String) id);
                }
            }
            assertEquals(fromApi, new TreeSet<>(Arrays.asList(expectedIds)));
        }
    }

    @Test
    public void culprits() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FakeChangeLogSCM scm = new FakeChangeLogSCM();
        p.setScm(scm);

        LogRotatorTest.StallBuilder sync = new LogRotatorTest.StallBuilder();

        // 1st build, successful, no culprits
        scm.addChange().withAuthor("alice");
        FreeStyleBuild b = j.buildAndAssertSuccess(p);
        assertCulprits(b, "alice");

        // 2nd build
        scm.addChange().withAuthor("bob");
        p.getBuildersList().add(new FailureBuilder());
        b = j.buildAndAssertStatus(Result.FAILURE, p);
        assertCulprits(b, "bob");

        // 3rd build. bob continues to be in culprit
        p.getBuildersList().add(sync);
        scm.addChange().withAuthor("charlie");
        b = p.scheduleBuild2(0).waitForStart();
        sync.waitFor(b.getNumber(), 1, TimeUnit.SECONDS);

        // Verify that we can get culprits while running.
        assertCulprits(b, "bob", "charlie");
        sync.release(b.getNumber());
        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
        assertCulprits(b, "bob", "charlie");

        // 4th build, unstable. culprit list should continue
        scm.addChange().withAuthor("dave");
        p.getBuildersList().replaceBy(Set.of(new UnstableBuilder()));
        b = j.buildAndAssertStatus(Result.UNSTABLE, p);
        assertCulprits(b, "bob", "charlie", "dave");

        // 5th build, unstable. culprit list should continue
        scm.addChange().withAuthor("eve");
        b = j.buildAndAssertStatus(Result.UNSTABLE, p);
        assertCulprits(b, "bob", "charlie", "dave", "eve");

        // 6th build, success, accumulation continues up to this point
        scm.addChange().withAuthor("fred");
        p.getBuildersList().clear();
        b = j.buildAndAssertSuccess(p);
        assertCulprits(b, "bob", "charlie", "dave", "eve", "fred");

        // 7th build, back to empty culprits
        scm.addChange().withAuthor("george");
        b = j.buildAndAssertSuccess(p);
        assertCulprits(b, "george");
    }

    @Issue("JENKINS-19920")
    @Test
    public void lastBuildNextBuild() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b1 = j.buildAndAssertSuccess(p);
        FreeStyleBuild b2 = j.buildAndAssertSuccess(p);
        assertEquals(b2, p.getLastBuild());
        b2.getNextBuild(); // force this to be initialized
        b2.delete();
        assertEquals(b1, p.getLastBuild());
        b1 = p.getLastBuild();
        assertNull(b1.getNextBuild());
    }

    @Test
    public void doNotInterruptBuildAbruptlyWhenExceptionThrownFromBuildStep() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new ThrowBuilder());
        FreeStyleBuild build = j.buildAndAssertStatus(Result.FAILURE, p);
        j.assertLogContains("Finished: FAILURE", build);
        j.assertLogContains("Build step 'ThrowBuilder' marked build as failure", build);
    }

    @Test
    public void fixEmptyDisplayName() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("foo");
        p.setDisplayName("");
        assertEquals("An empty display name should be ignored.", "foo", p.getDisplayName());
    }

    @Test
    public void fixBlankDisplayName() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("foo");
        p.setDisplayName(" ");
        assertEquals("A blank display name should be ignored.", "foo", p.getDisplayName());
    }

    @Test
    public void validDisplayName() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("foo");
        p.setDisplayName("bar");
        assertEquals("A non-blank display name should be used.", "bar", p.getDisplayName());
    }

    @Test
    public void trimValidDisplayName() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("foo");
        p.setDisplayName("    bar    ");
        assertEquals("A non-blank display name with whitespace should be trimmed.", "bar", p.getDisplayName());
    }

    private static class ThrowBuilder extends Builder {
        @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
            throw new NullPointerException();
        }

        @TestExtension("doNotInterruptBuildAbruptlyWhenExceptionThrownFromBuildStep")
        public static class DescriptorImpl extends Descriptor<Builder> {}
    }

    @Test
    @Issue("JENKINS-10615")
    public void workspaceLock() throws Exception {
        logging.record(Run.class, Level.FINER);
        FreeStyleProject p = j.createFreeStyleProject();
        p.setConcurrentBuild(true);
        OneShotEvent e1 = new OneShotEvent();
        OneShotEvent e2 = new OneShotEvent();
        OneShotEvent done = new OneShotEvent();

        p.getPublishersList().add(new Recorder() {
            @Override
            public BuildStepMonitor getRequiredMonitorService() {
                return BuildStepMonitor.NONE;
            }

            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
                if (build.number == 1) {
                    e1.signal();  // signal that build #1 is in publisher
                } else if (build.number == 2) {
                    e2.signal();  // signal that build #2 is in publisher
                } else {
                    throw new IllegalArgumentException("unexpected build number: " + build.number);
                }

                done.block();

                return true;
            }

            private Object writeReplace() {
                return new Object();
            }
        });

        QueueTaskFuture<FreeStyleBuild> b1 = p.scheduleBuild2(0);
        e1.block();

        QueueTaskFuture<FreeStyleBuild> b2 = p.scheduleBuild2(0);
        e2.block();

        // at this point both builds are in the publisher, so we verify that
        // the workspace are differently allocated
        assertNotEquals(b1.getStartCondition().get().getWorkspace(), b2.getStartCondition().get().getWorkspace());

        done.signal();
        Logger.getLogger(AbstractBuildTest.class.getName()).info("Test done, letting builds complete…");
        j.waitForCompletion(b1.get());
        j.waitForCompletion(b2.get());
        Logger.getLogger(AbstractBuildTest.class.getName()).info("…done.");
    }

    @Test
    @Issue("JENKINS-60634")
    public void tempDirVariable() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        if (Functions.isWindows()) {
            p.getBuildersList().add(new BatchFile("mkdir \"%WORKSPACE_TMP%\"\r\necho ok > \"%WORKSPACE_TMP%\\x\""));
        } else {
            p.getBuildersList().add(new Shell("set -u && mkdir -p \"$WORKSPACE_TMP\" && touch \"$WORKSPACE_TMP/x\""));
        }
        j.buildAndAssertSuccess(p);
        assertTrue(WorkspaceList.tempDir(j.jenkins.getWorkspaceFor(p)).child("x").exists());
    }

}
