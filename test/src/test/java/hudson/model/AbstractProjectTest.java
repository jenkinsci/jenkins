/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.tasks.Shell;
import hudson.scm.NullSCM;
import hudson.Launcher;
import hudson.FilePath;
import hudson.Util;
import hudson.tasks.ArtifactArchiver;
import hudson.util.StreamTaskListener;
import hudson.util.OneShotEvent;
import java.io.IOException;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;

import java.io.File;
import java.util.concurrent.Future;
import org.apache.commons.io.FileUtils;

/**
 * @author Kohsuke Kawaguchi
 */
public class AbstractProjectTest extends HudsonTestCase {
    /**
     * Tests the workspace deletion.
     */
    public void testWipeWorkspace() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new Shell("echo hello"));

        FreeStyleBuild b = project.scheduleBuild2(0).get();

        assertTrue("Workspace should exist by now",
                b.getWorkspace().exists());

        // emulate the user behavior
        WebClient webClient = new WebClient();
        HtmlPage page = webClient.getPage(project);

        page = (HtmlPage)page.getFirstAnchorByText("Workspace").click();
        page = (HtmlPage)page.getFirstAnchorByText("Wipe Out Workspace").click();
        page = (HtmlPage)((HtmlForm)page.getElementById("confirmation")).submit(null);

        assertFalse("Workspace should be gone by now",
                b.getWorkspace().exists());
    }

    /**
     * Makes sure that the workspace deletion is protected.
     */
    @PresetData(DataSet.NO_ANONYMOUS_READACCESS)
    public void testWipeWorkspaceProtected() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new Shell("echo hello"));

        FreeStyleBuild b = project.scheduleBuild2(0).get();

        assertTrue("Workspace should exist by now",b.getWorkspace().exists());

        // make sure that the action link is protected
        try {
            new WebClient().getPage(project,"doWipeOutWorkspace");
            fail("Should have failed");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(e.getStatusCode(),403);
        }
    }

    /**
     * Makes sure that the workspace deletion link is not provided
     * when the user doesn't have an access.
     */
    @PresetData(DataSet.ANONYMOUS_READONLY)
    public void testWipeWorkspaceProtected2() throws Exception {
        ((GlobalMatrixAuthorizationStrategy)hudson.getAuthorizationStrategy()).add(AbstractProject.WORKSPACE,"anonymous");

        // make sure that the deletion is protected in the same way
        testWipeWorkspaceProtected();

        // there shouldn't be any "wipe out workspace" link for anonymous user
        WebClient webClient = new WebClient();
        HtmlPage page = webClient.getPage(hudson.getItem("test0"));

        page = (HtmlPage)page.getFirstAnchorByText("Workspace").click();
        try {
            page.getFirstAnchorByText("Wipe Out Workspace");
            fail("shouldn't find a link");
        } catch (ElementNotFoundException e) {
            // OK
        }
    }

    /**
     * Tests the &lt;optionalBlock @field> round trip behavior by using {@link AbstractProject#concurrentBuild}
     */
    public void testOptionalBlockDataBindingRoundtrip() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        for( boolean b : new boolean[]{true,false}) {
            p.setConcurrentBuild(b);
            submit(new WebClient().getPage(p,"configure").getFormByName("config"));
            assertEquals(b,p.isConcurrentBuild());
        }
    }

    /**
     * Tests round trip configuration of the blockBuildWhenUpstreamBuilding field
     */
    @Bug(4423)
    public void testConfiguringBlockBuildWhenUpstreamBuildingRoundtrip() throws Exception {
        FreeStyleProject p = createFreeStyleProject();        
        p.blockBuildWhenUpstreamBuilding = false;
        
        HtmlForm form = new WebClient().getPage(p, "configure").getFormByName("config");
        HtmlInput input = form.getInputByName("blockBuildWhenUpstreamBuilding");
        assertFalse("blockBuildWhenUpstreamBuilding check box is checked.", input.isChecked());
        
        input.setChecked(true);
        submit(form);        
        assertTrue("blockBuildWhenUpstreamBuilding was not updated from configuration form", p.blockBuildWhenUpstreamBuilding);
        
        form = new WebClient().getPage(p, "configure").getFormByName("config");
        input = form.getInputByName("blockBuildWhenUpstreamBuilding");
        assertTrue("blockBuildWhenUpstreamBuilding check box is not checked.", input.isChecked());
    }

    /**
     * Unless the concurrent build option is enabled, polling and build should be mutually exclusive
     * to avoid allocating unnecessary workspaces.
     */
    @Bug(4202)
    public void testPollingAndBuildExclusion() throws Exception {
        final OneShotEvent sync = new OneShotEvent();

        final FreeStyleProject p = createFreeStyleProject();
        FreeStyleBuild b1 = buildAndAssertSuccess(p);

        p.setScm(new NullSCM() {
            @Override
            public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener) {
                try {
                    sync.block();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return true;
            }

            /**
             * Don't write 'this', so that subtypes can be implemented as anonymous class.
             */
            private Object writeReplace() { return new Object(); }
            
            @Override public boolean requiresWorkspaceForPolling() {
                return true;
            }
        });
        Thread t = new Thread() {
            @Override public void run() {
                p.pollSCMChanges(StreamTaskListener.fromStdout());
            }
        };
        try {
            t.start();
            Future<FreeStyleBuild> f = p.scheduleBuild2(0);

            // add a bit of delay to make sure that the blockage is happening
            Thread.sleep(3000);

            // release the polling
            sync.signal();

            FreeStyleBuild b2 = assertBuildStatusSuccess(f);

            // they should have used the same workspace.
            assertEquals(b1.getWorkspace(), b2.getWorkspace());
        } finally {
            t.interrupt();
        }
    }

    @Bug(1986)
    public void testBuildSymlinks() throws Exception {
        FreeStyleProject job = createFreeStyleProject();
        job.getBuildersList().add(new Shell("echo \"Build #$BUILD_NUMBER\"\n"));
        FreeStyleBuild build = job.scheduleBuild2(0, new Cause.UserCause()).get();
        File lastSuccessful = new File(job.getRootDir(), "lastSuccessful"),
             lastStable = new File(job.getRootDir(), "lastStable");
        // First build creates links
        assertSymlinkForBuild(lastSuccessful, 1);
        assertSymlinkForBuild(lastStable, 1);
        FreeStyleBuild build2 = job.scheduleBuild2(0, new Cause.UserCause()).get();
        // Another build updates links
        assertSymlinkForBuild(lastSuccessful, 2);
        assertSymlinkForBuild(lastStable, 2);
        // Delete latest build should update links
        build2.delete();
        assertSymlinkForBuild(lastSuccessful, 1);
        assertSymlinkForBuild(lastStable, 1);
        // Delete all builds should remove links
        build.delete();
        assertFalse("lastSuccessful link should be removed", lastSuccessful.exists());
        assertFalse("lastStable link should be removed", lastStable.exists());
    }

    private static void assertSymlinkForBuild(File file, int buildNumber)
            throws IOException, InterruptedException {
        assertTrue("should exist and point to something that exists", file.exists());
        assertTrue("should be symlink", Util.isSymlink(file));
        String s = FileUtils.readFileToString(new File(file, "log"));
        assertTrue("link should point to build #" + buildNumber + ", but link was: "
                   + Util.resolveSymlink(file, TaskListener.NULL) + "\nand log was:\n" + s,
                   s.contains("Build #" + buildNumber + "\n"));
    }

    @Bug(2543)
    public void testSymlinkForPostBuildFailure() throws Exception {
        // Links should be updated after post-build actions when final build result is known
        FreeStyleProject job = createFreeStyleProject();
        job.getBuildersList().add(new Shell("echo \"Build #$BUILD_NUMBER\"\n"));
        FreeStyleBuild build = job.scheduleBuild2(0, new Cause.UserCause()).get();
        assertEquals(Result.SUCCESS, build.getResult());
        File lastSuccessful = new File(job.getRootDir(), "lastSuccessful"),
             lastStable = new File(job.getRootDir(), "lastStable");
        // First build creates links
        assertSymlinkForBuild(lastSuccessful, 1);
        assertSymlinkForBuild(lastStable, 1);
        // Archive artifacts that don't exist to create failure in post-build action
        job.getPublishersList().add(new ArtifactArchiver("*.foo", "", false));
        build = job.scheduleBuild2(0, new Cause.UserCause()).get();
        assertEquals(Result.FAILURE, build.getResult());
        // Links should not be updated since build failed
        assertSymlinkForBuild(lastSuccessful, 1);
        assertSymlinkForBuild(lastStable, 1);
    }
}
