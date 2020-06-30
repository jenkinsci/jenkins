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

import hudson.FilePath;
import hudson.Launcher;
import hudson.tasks.ArtifactArchiver;
import java.io.IOException;
import java.net.HttpURLConnection;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
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
        j.assertBuildStatusSuccess(j.createFreeStyleProject("stuff").scheduleBuild2(0));
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
    private static final class Mgr extends ArtifactManager {
        static final AtomicBoolean deleted = new AtomicBoolean();
        @Override public boolean delete() throws IOException, InterruptedException {
            return !deleted.getAndSet(true);
        }
        @Override public void onLoad(Run<?, ?> build) {}
        @Override public void archive(FilePath workspace, Launcher launcher, BuildListener listener, Map<String, String> artifacts) throws IOException, InterruptedException {}
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
