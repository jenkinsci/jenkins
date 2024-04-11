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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import hudson.FilePath;
import hudson.Launcher;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Builder;
import hudson.tasks.Fingerprinter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import jenkins.model.ArtifactManager;
import jenkins.model.ArtifactManagerConfiguration;
import jenkins.model.ArtifactManagerFactory;
import jenkins.model.ArtifactManagerFactoryDescriptor;
import jenkins.model.Jenkins;
import jenkins.util.VirtualFile;
import org.htmlunit.ScriptResult;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SmokeTest;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

@Category(SmokeTest.class)
public class RunTest  {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Issue("JENKINS-17935")
    @Test public void getDynamicInvisibleTransientAction() throws Exception {
        TransientBuildActionFactory.all().add(0, new TransientBuildActionFactory() {
            @Override public Collection<? extends Action> createFor(Run target) {
                return Collections.singleton(new Action() {
                    @Override public String getDisplayName() {
                        return "Test";
                    }

                    @Override public String getIconFileName() {
                        return null;
                    }

                    @Override public String getUrlName() {
                        return null;
                    }
                });
            }
        });
        j.buildAndAssertSuccess(j.createFreeStyleProject("stuff"));
        j.createWebClient().assertFails("job/stuff/1/nonexistent", HttpURLConnection.HTTP_NOT_FOUND);
    }

    @Issue("JENKINS-40281")
    @Test public void getBadgeActions() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b = j.buildAndAssertSuccess(p);
        assertEquals(0, b.getBadgeActions().size());
        assertTrue(b.canToggleLogKeep());
        b.keepLog();
        List<BuildBadgeAction> badgeActions = b.getBadgeActions();
        assertEquals(1, badgeActions.size());
        assertEquals(Run.KeepLogBuildBadge.class, badgeActions.get(0).getClass());
    }

    @Issue("JENKINS-51819")
    @Test public void deleteArtifactsCustom() throws Exception {
        ArtifactManagerConfiguration.get().getArtifactManagerFactories().add(new Mgr.Factory());
        FreeStyleProject p = j.createFreeStyleProject();
        j.jenkins.getWorkspaceFor(p).child("f").write("", null);
        p.getPublishersList().add(new ArtifactArchiver("f"));
        FreeStyleBuild b = j.buildAndAssertSuccess(p);
        b.delete();
        assertTrue(Mgr.deleted.get());
    }

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
        htmlPage.executeJavaScript("document.querySelector('#jenkins-build-history .app-builds-container__item__inner__controls svg')._tippy.show()");
        wc.waitForBackgroundJavaScript(500);
        ScriptResult result = htmlPage.executeJavaScript("document.querySelector('.tippy-content').innerHTML;");
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

        FullNameChangingProject(ItemGroup parent, String name) {
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

    public static final class Mgr extends ArtifactManager {
        static final AtomicBoolean deleted = new AtomicBoolean();

        @Override public boolean delete() {
            return !deleted.getAndSet(true);
        }

        @Override public void onLoad(Run<?, ?> build) {}

        @Override public void archive(FilePath workspace, Launcher launcher, BuildListener listener, Map<String, String> artifacts) {}

        @Override public VirtualFile root() {
            return VirtualFile.forFile(Jenkins.get().getRootDir()); // irrelevant
        }

        public static final class Factory extends ArtifactManagerFactory {
            @DataBoundConstructor public Factory() {}

            @Override public ArtifactManager managerFor(Run<?, ?> build) {
                return new Mgr();
            }

            @TestExtension("deleteArtifactsCustom") public static final class DescriptorImpl extends ArtifactManagerFactoryDescriptor {}
        }
    }

}
