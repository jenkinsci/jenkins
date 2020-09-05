/*
 * The MIT License
 *
 * Copyright (c) 2020 CloudBees, Inc.
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

import hudson.tasks.BuildTrigger;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;

public class UpstreamCauseSEC1901Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("SECURITY-1901")
    public void preventXssInUpstreamDisplayName() throws Exception {
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
    public void preventXssInUpstreamDisplayName_deleted() throws Exception {
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
    public void preventXssInUpstreamShortDescription() throws Exception {
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

        assertFalse("XSS not prevented", alertCalled.get());
    }

    private <B extends Build<?, B>> B waitForDownBuild(Project<?, B> down) throws Exception {
        j.waitUntilNoActivity();
        B result = down.getBuilds().getLastBuild();

        return result;
    }

    public static class CustomBuild extends Build<FullNameChangingProject, CustomBuild> {
        public CustomBuild(FullNameChangingProject job) throws IOException {
            super(job);
        }
    }

    static class FullNameChangingProject extends Project<FullNameChangingProject, CustomBuild> implements TopLevelItem {
        private volatile String virtualName;

        public FullNameChangingProject(ItemGroup parent, String name) {
            super(parent, name);
        }

        public void setVirtualName(String virtualName) {
            this.virtualName = virtualName;
        }

        @Override
        public String getName() {
            if (virtualName != null) {
                return virtualName;
            } else {
                return super.getName();
            }
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
