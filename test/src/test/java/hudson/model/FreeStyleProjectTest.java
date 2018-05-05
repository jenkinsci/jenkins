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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import java.io.ByteArrayInputStream;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import java.util.List;
import java.io.File;

/**
 * @author Kohsuke Kawaguchi
 */
public class FreeStyleProjectTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Tests a trivial configuration round-trip.
     *
     * The goal is to catch a P1-level issue that prevents all the form submissions to fail.
     */
    @Test
    public void configSubmission() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        Shell shell = new Shell("echo hello");
        project.getBuildersList().add(shell);

        // emulate the user behavior
        WebClient webClient = j.createWebClient();
        HtmlPage page = webClient.getPage(project,"configure");

        HtmlForm form = page.getFormByName("config");
        j.submit(form);

        List<Builder> builders = project.getBuilders();
        assertEquals(1,builders.size());
        assertEquals(Shell.class,builders.get(0).getClass());
        assertEquals("echo hello",((Shell)builders.get(0)).getCommand().trim());
        assertTrue(builders.get(0)!=shell);
    }

    /**
     * Custom workspace and concurrent build had a bad interaction.
     */
    @Test
    @Issue("JENKINS-4206")
    public void customWorkspaceAllocation() throws Exception {
        FreeStyleProject f = j.createFreeStyleProject();
        File d = j.createTmpDir();
        f.setCustomWorkspace(d.getPath());
        j.buildAndAssertSuccess(f);
    }

    /**
     * Custom workspace and variable expansion.
     */
    @Test
    @Issue("JENKINS-3997")
    public void customWorkspaceVariableExpansion() throws Exception {
        FreeStyleProject f = j.createFreeStyleProject();
        File d = new File(j.createTmpDir(),"${JOB_NAME}");
        f.setCustomWorkspace(d.getPath());
        FreeStyleBuild b = j.buildAndAssertSuccess(f);

        String path = b.getWorkspace().getRemote();
        System.out.println(path);
        assertFalse(path.contains("${JOB_NAME}"));
        assertEquals(b.getWorkspace().getName(),f.getName());
    }

    @Test
    @Issue("JENKINS-15817")
    public void minimalConfigXml() throws Exception {
        // Make sure it can be created without exceptions:
        FreeStyleProject project = (FreeStyleProject) j.jenkins.createProjectFromXML("stuff", new ByteArrayInputStream("<project/>".getBytes()));
        System.out.println(project.getConfigFile().asString());
        // and round-tripped:
        Shell shell = new Shell("echo hello");
        project.getBuildersList().add(shell);
        WebClient webClient = j.createWebClient();
        HtmlPage page = webClient.getPage(project,"configure");
        HtmlForm form = page.getFormByName("config");
        j.submit(form);
        List<Builder> builders = project.getBuilders();
        assertEquals(1,builders.size());
        assertEquals(Shell.class,builders.get(0).getClass());
        assertEquals("echo hello",((Shell)builders.get(0)).getCommand().trim());
        assertTrue(builders.get(0)!=shell);
        System.out.println(project.getConfigFile().asString());
    }

    @Test
    @Issue("JENKINS-36629")
    public void buildStabilityReports() throws Exception {
        for (int i = 0; i <= 32; i++) {
            FreeStyleProject p = j.createFreeStyleProject(String.format("Pattern-%s", Integer.toBinaryString(i)));
            int expectedFails = 0;
            for (int j = 32; j >= 1; j = j / 2) {
                p.getBuildersList().clear();
                if ((i & j) == j) {
                    p.getBuildersList().add(new FailureBuilder());
                    if (j <= 16) {
                        expectedFails++;
                    }
                }
                p.scheduleBuild2(0).get();
            }
            HealthReport health = p.getBuildHealth();

            assertThat(String.format("Pattern %s score", Integer.toBinaryString(i)), health.getScore(), is(100*(5-expectedFails)/5));
        }
    }
}
