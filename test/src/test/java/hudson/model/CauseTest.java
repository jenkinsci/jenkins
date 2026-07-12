/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.XmlFile;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.BuildTrigger;
import hudson.triggers.SCMTrigger;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import org.xml.sax.SAXException;

@WithJenkins
class CauseTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("JENKINS-14814")
    @Test
    void deeplyNestedCauses() throws Exception {
        FreeStyleProject a = j.createFreeStyleProject("a");
        FreeStyleProject b = j.createFreeStyleProject("b");
        FreeStyleBuild early = null;
        FreeStyleBuild last = null;
        for (int i = 1; i <= 15; i++) {
            last = j.waitForCompletion(a.scheduleBuild2(0, last == null ? null : new Cause.UpstreamCause(last)).get());
            last = j.waitForCompletion(b.scheduleBuild2(0, new Cause.UpstreamCause(last)).get());
            if (i == 5) {
                early = last;
            }
        }
        String buildXml = new XmlFile(Run.XSTREAM, new File(early.getRootDir(), "build.xml")).asString();
        assertTrue(buildXml.contains("<upstreamBuild>1</upstreamBuild>"), "keeps full history:\n" + buildXml);
        buildXml = new XmlFile(Run.XSTREAM, new File(last.getRootDir(), "build.xml")).asString();
        assertFalse(buildXml.contains("<upstreamBuild>1</upstreamBuild>"), "too big:\n" + buildXml);
    }

    @Issue("JENKINS-15747")
    @Test
    void broadlyNestedCauses() throws Exception {
        FreeStyleProject a = j.createFreeStyleProject("a");
        FreeStyleProject b = j.createFreeStyleProject("b");
        FreeStyleProject c = j.createFreeStyleProject("c");
        List<QueueTaskFuture<FreeStyleBuild>> futures = new ArrayList<>();
        Run<?, ?> last = null;
        for (int i = 1; i <= 10; i++) {
            Cause cause = last == null ? null : new Cause.UpstreamCause(last);
            last = j.waitForCompletion(a.scheduleBuild2(0, cause).get());
            recordFuture(b.scheduleBuild2(1, cause), futures);
            cause = new Cause.UpstreamCause(last);
            last = j.waitForCompletion(b.scheduleBuild2(0, cause).get());
            recordFuture(c.scheduleBuild2(1, cause), futures);
            cause = new Cause.UpstreamCause(last);
            last = j.waitForCompletion(c.scheduleBuild2(0, cause).get());
            recordFuture(a.scheduleBuild2(1, cause), futures);
        }
        last = j.waitForCompletion(a.scheduleBuild2(0, new Cause.UpstreamCause(last)).get());
        int count = new XmlFile(Run.XSTREAM, new File(last.getRootDir(), "build.xml")).asString().split(Pattern.quote("<hudson.model.Cause_-UpstreamCause")).length;
        assertFalse(count > 100, "too big at " + count);
        //j.interactiveBreak();
        for (QueueTaskFuture<FreeStyleBuild> future : futures) {
            j.assertBuildStatusSuccess(j.waitForCompletion(future.waitForStart()));
        }
    }

    private static QueueTaskFuture<FreeStyleBuild> recordFuture(QueueTaskFuture<FreeStyleBuild> future, List<QueueTaskFuture<FreeStyleBuild>> futures) {
        futures.add(future);
        return future;
    }

    @Issue("JENKINS-48467")
    @Test
    void userIdCausePrintTest() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TaskListener listener = new StreamTaskListener(baos, Charset.defaultCharset());

        //null userId - print unknown or anonymous
        Cause causeA = new Cause.UserIdCause(null);
        causeA.print(listener);

        assertEquals("Started by user unknown or anonymous", baos.toString(Charset.defaultCharset()).trim());
        baos.reset();

        //SYSTEM userid  - getDisplayName() should be SYSTEM
        Cause causeB = new Cause.UserIdCause();
        causeB.print(listener);

        assertThat(baos.toString(Charset.defaultCharset()), containsString("SYSTEM"));
        baos.reset();

        //unknown userid - print unknown or anonymous
        Cause causeC = new Cause.UserIdCause("abc123");
        causeC.print(listener);

        assertEquals("Started by user unknown or anonymous", baos.toString(Charset.defaultCharset()).trim());
        baos.reset();

        //More or less standard operation
        //user userid  - getDisplayName() should be foo
        User user = User.getById("foo", true);
        Cause causeD = new Cause.UserIdCause(user.getId());
        causeD.print(listener);

        assertThat(baos.toString(Charset.defaultCharset()), containsString(user.getDisplayName()));
        baos.reset();
    }

    @Test
    @Issue("SECURITY-1960")
    @LocalData
    void xssInRemoteCause() throws IOException, SAXException {
        final Item item = j.jenkins.getItemByFullName("fs");
        assertThat(item, instanceOf(FreeStyleProject.class));
        FreeStyleProject fs = (FreeStyleProject) item;
        final FreeStyleBuild build = fs.getBuildByNumber(1);

        final JenkinsRule.WebClient wc = j.createWebClient();
        final String content = wc.getPage(build).getWebResponse().getContentAsString();
        assertFalse(content.contains("Started by remote host <img"));
        assertTrue(content.contains("Started by remote host &lt;img"));
    }

    @Test
    @Issue("SECURITY-1901")
    void preventXssInUpstreamDisplayName() throws Exception {
        j.jenkins.setQuietPeriod(0);
        FreeStyleProject up = j.createFreeStyleProject("up");
        up.setDisplayName("Up<script>alert(123)</script>Project");

        FreeStyleProject down = j.createFreeStyleProject("down");

        up.getPublishersList().add(new BuildTrigger(down.getFullName(), false));

        j.jenkins.rebuildDependencyGraph();

        j.buildAndAssertSuccess(up);

        FreeStyleBuild downBuild = this.waitForDownBuild(down);

        ensureXssIsPrevented(downBuild);
    }

    @Test
    @Issue("SECURITY-1901")
    void preventXssInUpstreamDisplayName_deleted() throws Exception {
        j.jenkins.setQuietPeriod(0);
        FreeStyleProject up = j.createFreeStyleProject("up");
        up.setDisplayName("Up<script>alert(123)</script>Project");

        FreeStyleProject down = j.createFreeStyleProject("down");

        up.getPublishersList().add(new BuildTrigger(down.getFullName(), false));

        j.jenkins.rebuildDependencyGraph();

        FreeStyleBuild upBuild = j.buildAndAssertSuccess(up);

        FreeStyleBuild downBuild = this.waitForDownBuild(down);

        // that will display a different part
        upBuild.delete();

        ensureXssIsPrevented(downBuild);
    }

    @Test
    @Issue("SECURITY-1901")
    void preventXssInUpstreamShortDescription() throws Exception {
        FullNameChangingProject up = j.createProject(FullNameChangingProject.class, "up");

        FreeStyleProject down = j.createFreeStyleProject("down");

        CustomBuild upBuild = j.buildAndAssertSuccess(up);

        up.setVirtualName("Up<script>alert(123)</script>Project");
        j.assertBuildStatusSuccess(down.scheduleBuild2(0, new Cause.UpstreamCause(upBuild)));
        up.setVirtualName(null);

        FreeStyleBuild downBuild = this.waitForDownBuild(down);

        ensureXssIsPrevented(downBuild);
    }

    private void ensureXssIsPrevented(FreeStyleBuild downBuild) throws Exception {
        AtomicBoolean alertCalled = new AtomicBoolean(false);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.setAlertHandler((page, s) -> alertCalled.set(true));
        wc.goTo(downBuild.getUrl());

        assertFalse(alertCalled.get(), "XSS not prevented");
    }

    private <B extends Build<?, B>> B waitForDownBuild(Project<?, B> down) throws Exception {
        j.waitUntilNoActivity();
        B result = down.getBuilds().getLastBuild();

        return result;
    }


    @Test
    @Issue("SECURITY-2452")
    void basicCauseIsSafe() throws Exception {
        final FreeStyleProject fs = j.createFreeStyleProject();
        {
            final FreeStyleBuild build = j.waitForCompletion(fs.scheduleBuild2(0, new SimpleCause("safe")).get());

            final JenkinsRule.WebClient wc = j.createWebClient();
            final String content = wc.getPage(build).getWebResponse().getContentAsString();
            assertTrue(content.contains("Simple cause: safe"));
        }
        {
            final FreeStyleBuild build = j.waitForCompletion(fs.scheduleBuild2(0, new SimpleCause("<img src=x onerror=alert(1)>")).get());

            final JenkinsRule.WebClient wc = j.createWebClient();
            final String content = wc.getPage(build).getWebResponse().getContentAsString();
            assertFalse(content.contains("Simple cause: <img"));
            assertTrue(content.contains("Simple cause: &lt;img"));
        }
    }

    @Test
    @LocalData
    void upstreamCauseOnLoad() throws Exception {
        final Item item = j.jenkins.getItemByFullName("downstream");
        assertThat(item, instanceOf(FreeStyleProject.class));
        FreeStyleProject down = (FreeStyleProject) item;
        final FreeStyleBuild build = down.getBuildByNumber(1);
        Cause.UpstreamCause upstreamCause = (Cause.UpstreamCause) build.getCauses().getFirst();
        SCMTrigger.SCMTriggerCause scmCause = (SCMTrigger.SCMTriggerCause) upstreamCause.getUpstreamCauses().getFirst();
        assertNotNull(scmCause.getRun());
    }

    public static class SimpleCause extends Cause {
        private final String description;

        @SuppressWarnings("checkstyle:redundantmodifier")
        public SimpleCause(String description) {
            this.description = description;
        }

        @Override
        public String getShortDescription() {
            return "Simple cause: " + description;
        }
    }

    public static class CustomBuild extends Build<FullNameChangingProject, CustomBuild> {
        @SuppressWarnings("checkstyle:redundantmodifier")
        public CustomBuild(FullNameChangingProject job) throws IOException {
            super(job);
        }
    }

    static class FullNameChangingProject extends Project<FullNameChangingProject, CustomBuild> implements TopLevelItem {
        private volatile String virtualName;

        FullNameChangingProject(ItemGroup parent, String name) {
            super(parent, name);
        }

        public void setVirtualName(String virtualName) {
            this.virtualName = virtualName;
        }

        @NonNull
        @Override
        public String getName() {
            return Objects.requireNonNullElseGet(virtualName, super::getName);
        }

        @Override
        protected Class<CustomBuild> getBuildClass() {
            return CustomBuild.class;
        }

        @Override
        public TopLevelItemDescriptor getDescriptor() {
            return (FreeStyleProject.DescriptorImpl) Jenkins.get().getDescriptorOrDie(getClass());
        }

        @TestExtension
        public static class DescriptorImpl extends AbstractProjectDescriptor {

            @Override
            public FullNameChangingProject newInstance(ItemGroup parent, String name) {
                return new FullNameChangingProject(parent, name);
            }
        }
    }
}
