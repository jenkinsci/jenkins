/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc. and other Jenkins contributors
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
package jenkins.model.logging;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import jenkins.model.logging.impl.CompatFileLogStorage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;

import javax.annotation.CheckForNull;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;


@For(LogStorageFactory.class)
public class LogStorageFactoryTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    private MockLogStorageFactory factory;

    @Before
    public void setup() {
        factory = j.jenkins.getExtensionList(LogStorageFactory.class)
                .get(MockLogStorageFactory.class);
        factory.setBaseDir(tmpDir.getRoot());
    }

    @Test
    @Issue("JENKINS-38313")
    public void shouldSetLogStorageWhenRequired() throws Exception {
        FreeStyleProject prj = j.createFreeStyleProject();
        prj.getBuildersList().add(
                hudson.remoting.Launcher.isWindows()
                        ? new BatchFile("echo Hello")
                        : new Shell("echo Hello")
        );
        factory.alterLogStorageFor(prj);

        final FreeStyleBuild build = j.buildAndAssertSuccess(prj);
        factory.assertWasInvokedFor(build);
        assertThat(build.getLogStorage(), instanceOf(MockLogStorage.class));
        assertThat(build.getLogFile(), equalTo(factory.getLogFor(build)));
        assertThat(build.getLog(), containsString("Hello"));
        assertTrue(build.getLogFile().exists());
    }

    @Test
    @Issue("JENKINS-38313")
    public void shouldFallbackToTheDefaultFileStorage() throws Exception {
        FreeStyleProject prj = j.createFreeStyleProject();
        prj.getBuildersList().add(
                hudson.remoting.Launcher.isWindows()
                        ? new BatchFile("echo Hello")
                        : new Shell("echo Hello")
        );

        final FreeStyleBuild build = j.buildAndAssertSuccess(prj);
        factory.assertWasInvokedFor(build);
        assertThat(build.getLogStorage(), instanceOf(CompatFileLogStorage.class));
        assertThat(build.getLog(), containsString("Hello"));
        assertTrue(build.getLogFile().exists());
    }

    /**
     * Loads job from configuration which has no storage section.
     */
    @Test
    @Issue("JENKINS-38313")
    @LocalData
    public void backwardCompatibility() throws Exception {
        FreeStyleProject prj = (FreeStyleProject)j.jenkins.getItem("parameterized");
        FreeStyleBuild build = prj.getBuildByNumber(1);
        assertThat(build.getLogStorage(), instanceOf(CompatFileLogStorage.class));
    }

    @TestExtension
    public static final class MockLogStorageFactory extends LogStorageFactory {

        @CheckForNull File baseDir;

        private transient Set<Job<?, ?>> jobCache = new HashSet<>();
        private transient Map<Loggable, File> logCache = new HashMap<>();

        private transient Set<Loggable> invocationList = new HashSet<>();

        public void setBaseDir(File baseDir) {
            this.baseDir = baseDir;
        }

        public void alterLogStorageFor(Job<?, ?> job) {
            jobCache.add(job);
        }

        @CheckForNull
        public File getLogFor(Loggable loggable) {
            return logCache.get(loggable);
        }

        @Override
        protected LogStorage getLogStorage(Loggable object) {
            invocationList.add(object);

            if (object instanceof Run) {
                Run<?, ?> run = (Run<?, ?>) object;
                Job<?, ?> parent = run.getParent();
                if (jobCache.contains(parent)) {
                    File logDestination = new File(baseDir, parent.getFullName() + "/" + run.getNumber() + ".txt");
                    logDestination.getParentFile().mkdirs();
                    logCache.put(object, logDestination);
                    return new MockLogStorage(object, logDestination);
                }
            }

            return null;
        }

        public void assertWasInvokedFor(Loggable object) throws AssertionError {
            if (!invocationList.contains(object)) {
                throw new AssertionError("Log storage factory was not invoked for " + object);
            }
        }
    }
}
