/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jorg Heymans
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

import com.gargoylesoftware.htmlunit.ScriptResult;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Launcher;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Builder;
import hudson.tasks.Fingerprinter;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;

public class RunSEC1902Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Issue("SECURITY-1902")
    @Test public void preventXssInBadgeTooltip() throws Exception {
        j.jenkins.setQuietPeriod(0);
        /*
         * The scenario to trigger is to have a build protected from deletion because of an upstream protected build.
         * Once we have that condition, we need to ensure the upstream project has a dangerous name
         */
        FreeStyleProject up = j.createFreeStyleProject("up");
        up.getBuildersList().add(new WriteFileStep());
        up.getPublishersList().add(new Fingerprinter("**/*"));

        FullNameChangingProject down = j.createProject(FullNameChangingProject.class, "down");
        down.getBuildersList().add(new WriteFileStep());
        down.getPublishersList().add(new Fingerprinter("**/*"));
        // protected field, we are in the same package
        down.keepDependencies = true;

        up.getPublishersList().add(new BuildTrigger(down.getFullName(), false));

        j.jenkins.rebuildDependencyGraph();

        FreeStyleBuild upBuild = j.buildAndAssertSuccess(up);
        j.waitUntilNoActivity();
        CustomBuild downBuild = down.getBuilds().getLastBuild();
        assertNotNull("The down build must exist, otherwise the up's one is not protected.", downBuild);

        // updating the name before the build is problematic under Windows
        // so we are updating internal stuff manually
        String newName = "Down<img src=x onerror=alert(123)>Project";
        down.setVirtualName(newName);
        Fingerprint f = upBuild.getAction(Fingerprinter.FingerprintAction.class).getFingerprints().get("test.txt");
        f.add(newName, 1);

        // keeping the minimum to validate it's working and it's not exploitable as there are some modifications
        // like adding double quotes
        ensureXssIsPrevented(up, "Down", "<img");
    }

    private void ensureXssIsPrevented(FreeStyleProject upProject, String validationPart, String dangerousPart) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage htmlPage = wc.goTo(upProject.getUrl());

        // trigger the tooltip display
        htmlPage.executeJavaScript("document.querySelector('#buildHistory table .build-badge img').dispatchEvent(new Event('mouseover'));");
        wc.waitForBackgroundJavaScript(500);
        ScriptResult result = htmlPage.executeJavaScript("document.querySelector('#tt').innerHTML;");
        Object jsResult = result.getJavaScriptResult();
        assertThat(jsResult, instanceOf(String.class));
        String jsResultString = (String) jsResult;

        assertThat("The tooltip does not work as expected", jsResultString, containsString(validationPart));
        assertThat("XSS not prevented", jsResultString, not(containsString(dangerousPart)));
    }

    public static class WriteFileStep extends Builder {
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            build.getWorkspace().child("test.txt").write("123", StandardCharsets.UTF_8.name());
            return true;
        }
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

        @TestExtension("preventXssInBadgeTooltip")
        public static class DescriptorImpl extends AbstractProjectDescriptor {

            @Override
            public FullNameChangingProject newInstance(ItemGroup parent, String name) {
                return new FullNameChangingProject(parent, name);
            }
        }
    }
}
