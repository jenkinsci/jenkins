/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

import hudson.EnvVars;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.HudsonTestCase.WebClient;

import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;

public class RunParameterDefinitionTest extends HudsonTestCase {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Bug(16462)
    @Test public void inFolders() throws Exception {
        MockFolder dir = j.createFolder("dir");
        MockFolder subdir = dir.createProject(MockFolder.class, "sub dir");
        FreeStyleProject p = subdir.createProject(FreeStyleProject.class, "some project");
        p.scheduleBuild2(0).get();
        FreeStyleBuild build2 = p.scheduleBuild2(0).get();
        p.scheduleBuild2(0).get();
        String id = build2.getExternalizableId();
        assertEquals("dir/sub dir/some project#2", id);
        assertEquals(build2, Run.fromExternalizableId(id));
        RunParameterDefinition def = new RunParameterDefinition("build", "dir/sub dir/some project", "my build", null);
        assertEquals("dir/sub dir/some project", def.getProjectName());
        assertEquals(p, def.getProject());
        EnvVars env = new EnvVars();
        def.getDefaultParameterValue().buildEnvVars(null, env);
        assertEquals(j.jenkins.getRootUrl() + "job/dir/job/sub%20dir/job/some%20project/3/", env.get("build"));
        RunParameterValue val = def.createValue(id);
        assertEquals(build2, val.getRun());
        assertEquals("dir/sub dir/some project", val.getJobName());
        assertEquals("2", val.getNumber());
        val.buildEnvVars(null, env);
        assertEquals(j.jenkins.getRootUrl() + "job/dir/job/sub%20dir/job/some%20project/2/", env.get("build"));
        assertEquals("dir/sub dir/some project", env.get("build.jobName"));
        assertEquals("dir/sub dir/some project", env.get("build_JOBNAME"));
        assertEquals("2", env.get("build.number"));
        assertEquals("2", env.get("build_NUMBER"));
    }

    @Test
    public void testNullThreshold() throws Exception {
        FreeStyleProject childProject = jenkins.createProject(FreeStyleProject.class, "childProject");
        FreeStyleBuild successfulBuild = childProject.scheduleBuild2(0).get();
        successfulBuild.setResult(Result.SUCCESS);

        FreeStyleBuild unstableBuild = childProject.scheduleBuild2(0).get();
        unstableBuild.setResult(Result.UNSTABLE);

        FreeStyleBuild failedBuild = childProject.scheduleBuild2(0).get();
        failedBuild.setResult(Result.FAILURE);

        FreeStyleBuild abortedBuild = childProject.scheduleBuild2(0).get();
        abortedBuild.setResult(Result.ABORTED);

        FreeStyleBuild notBuiltBuild = childProject.scheduleBuild2(0).get();
        notBuiltBuild.setResult(Result.NOT_BUILT);

        FreeStyleProject project = createFreeStyleProject();
        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
                new RunParameterDefinition("run", childProject.getName(), "run description", null));
        project.addProperty(pdp);
        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        WebClient wc = new WebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.goTo("/job/" + project.getName() + "/build?delay=0sec");

        HtmlForm form = page.getFormByName("parameters");

        HtmlElement element = (HtmlElement) form.selectSingleNode(".//tr[td/div/input/@value='run']");
        assertNotNull(element);
        assertEquals("run description", ((HtmlElement) element.selectSingleNode("td/div")).getAttribute("description"));
        assertEquals("run", ((HtmlElement) element.selectSingleNode("td[@class='setting-name']")).getTextContent());

        submit(form);
        Queue.Item q = jenkins.getQueue().getItem(project);
        if (q != null) q.getFuture().get();
        else Thread.sleep(1000);

        assertEquals(jenkins.getRootUrl() + childProject.getLastBuild().getUrl(), builder.getEnvVars().get("RUN"));
    }

    @Test
    public void testALLThreshold() throws Exception {
        FreeStyleProject childProject = jenkins.createProject(FreeStyleProject.class, "childProject");
        FreeStyleBuild successfulBuild = childProject.scheduleBuild2(0).get();
        successfulBuild.setResult(Result.SUCCESS);

        FreeStyleBuild unstableBuild = childProject.scheduleBuild2(0).get();
        unstableBuild.setResult(Result.UNSTABLE);

        FreeStyleBuild failedBuild = childProject.scheduleBuild2(0).get();
        failedBuild.setResult(Result.FAILURE);

        FreeStyleBuild abortedBuild = childProject.scheduleBuild2(0).get();
        abortedBuild.setResult(Result.ABORTED);

        FreeStyleBuild notBuiltBuild = childProject.scheduleBuild2(0).get();
        notBuiltBuild.setResult(Result.NOT_BUILT);

        FreeStyleProject project = createFreeStyleProject();
        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
                new RunParameterDefinition("run", childProject.getName(), "run description", "ALL"));
        project.addProperty(pdp);
        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        WebClient wc = new WebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.goTo("/job/" + project.getName() + "/build?delay=0sec");

        HtmlForm form = page.getFormByName("parameters");

        HtmlElement element = (HtmlElement) form.selectSingleNode(".//tr[td/div/input/@value='run']");
        assertNotNull(element);
        assertEquals("run description", ((HtmlElement) element.selectSingleNode("td/div")).getAttribute("description"));
        assertEquals("run", ((HtmlElement) element.selectSingleNode("td[@class='setting-name']")).getTextContent());

        submit(form);
        Queue.Item q = jenkins.getQueue().getItem(project);
        if (q != null) q.getFuture().get();
        else Thread.sleep(1000);

        assertEquals(jenkins.getRootUrl() + childProject.getLastBuild().getUrl(), builder.getEnvVars().get("RUN"));
    }

    @Test
    public void testSUCCESSThreshold() throws Exception {
        FreeStyleProject childProject = jenkins.createProject(FreeStyleProject.class, "childProject");

        FreeStyleBuild successfulBuild = childProject.scheduleBuild2(0).get();
        successfulBuild.setResult(Result.SUCCESS);

        FreeStyleBuild unstableBuild = childProject.scheduleBuild2(0).get();
        unstableBuild.setResult(Result.UNSTABLE);

        FreeStyleBuild failedBuild = childProject.scheduleBuild2(0).get();
        failedBuild.setResult(Result.FAILURE);

        FreeStyleBuild abortedBuild = childProject.scheduleBuild2(0).get();
        abortedBuild.setResult(Result.ABORTED);

        FreeStyleBuild notBuiltBuild = childProject.scheduleBuild2(0).get();
        notBuiltBuild.setResult(Result.NOT_BUILT);

        FreeStyleProject project = createFreeStyleProject();
        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
                new RunParameterDefinition("run", childProject.getName(), "run description", "SUCCESS"));
        project.addProperty(pdp);
        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        WebClient wc = new WebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.goTo("/job/" + project.getName() + "/build?delay=0sec");

        HtmlForm form = page.getFormByName("parameters");

        HtmlElement element = (HtmlElement) form.selectSingleNode(".//tr[td/div/input/@value='run']");
        assertNotNull(element);
        assertEquals("run description", ((HtmlElement) element.selectSingleNode("td/div")).getAttribute("description"));
        assertEquals("run", ((HtmlElement) element.selectSingleNode("td[@class='setting-name']")).getTextContent());

        submit(form);
        Queue.Item q = jenkins.getQueue().getItem(project);
        if (q != null) q.getFuture().get();
        else Thread.sleep(1000);

        assertEquals(jenkins.getRootUrl() + childProject.getLastStableBuild().getUrl(), builder.getEnvVars().get("RUN"));
    }

    @Test
    public void testUNSTABLEThreshold() throws Exception {
        FreeStyleProject childProject = jenkins.createProject(FreeStyleProject.class, "childProject");
        FreeStyleBuild successfulBuild = childProject.scheduleBuild2(0).get();
        successfulBuild.setResult(Result.SUCCESS);

        FreeStyleBuild unstableBuild = childProject.scheduleBuild2(0).get();
        unstableBuild.setResult(Result.UNSTABLE);

        FreeStyleBuild failedBuild = childProject.scheduleBuild2(0).get();
        failedBuild.setResult(Result.FAILURE);

        FreeStyleBuild abortedBuild = childProject.scheduleBuild2(0).get();
        abortedBuild.setResult(Result.ABORTED);

        FreeStyleBuild notBuiltBuild = childProject.scheduleBuild2(0).get();
        notBuiltBuild.setResult(Result.NOT_BUILT);

        FreeStyleProject project = createFreeStyleProject();
        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
                new RunParameterDefinition("run", childProject.getName(), "run description", "UNSTABLE"));
        project.addProperty(pdp);
        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        WebClient wc = new WebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.goTo("/job/" + project.getName() + "/build?delay=0sec");

        HtmlForm form = page.getFormByName("parameters");

        HtmlElement element = (HtmlElement) form.selectSingleNode(".//tr[td/div/input/@value='run']");
        assertNotNull(element);
        assertEquals("run description", ((HtmlElement) element.selectSingleNode("td/div")).getAttribute("description"));
        assertEquals("run", ((HtmlElement) element.selectSingleNode("td[@class='setting-name']")).getTextContent());

        submit(form);
        Queue.Item q = jenkins.getQueue().getItem(project);
        if (q != null) q.getFuture().get();
        else Thread.sleep(1000);

        assertEquals(jenkins.getRootUrl() + childProject.getLastUnstableBuild().getUrl(), builder.getEnvVars().get("RUN"));
    }

    @Test
    public void testFAILUREThreshold() throws Exception {
        FreeStyleProject childProject = jenkins.createProject(FreeStyleProject.class, "childProject");
        FreeStyleBuild successfulBuild = childProject.scheduleBuild2(0).get();
        successfulBuild.setResult(Result.SUCCESS);

        FreeStyleBuild unstableBuild = childProject.scheduleBuild2(0).get();
        unstableBuild.setResult(Result.UNSTABLE);

        FreeStyleBuild failedBuild = childProject.scheduleBuild2(0).get();
        failedBuild.setResult(Result.FAILURE);

        FreeStyleBuild abortedBuild = childProject.scheduleBuild2(0).get();
        abortedBuild.setResult(Result.ABORTED);

        FreeStyleBuild notBuiltBuild = childProject.scheduleBuild2(0).get();
        notBuiltBuild.setResult(Result.NOT_BUILT);

        FreeStyleProject project = createFreeStyleProject();
        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
                new RunParameterDefinition("run", childProject.getName(), "run description", "FAILURE"));
        project.addProperty(pdp);
        CaptureEnvironmentBuilder builder = new CaptureEnvironmentBuilder();
        project.getBuildersList().add(builder);

        WebClient wc = new WebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.goTo("/job/" + project.getName() + "/build?delay=0sec");

        HtmlForm form = page.getFormByName("parameters");

        HtmlElement element = (HtmlElement) form.selectSingleNode(".//tr[td/div/input/@value='run']");
        assertNotNull(element);
        assertEquals("run description", ((HtmlElement) element.selectSingleNode("td/div")).getAttribute("description"));
        assertEquals("run", ((HtmlElement) element.selectSingleNode("td[@class='setting-name']")).getTextContent());

        submit(form);
        Queue.Item q = jenkins.getQueue().getItem(project);
        if (q != null) q.getFuture().get();
        else Thread.sleep(1000);

        assertEquals(jenkins.getRootUrl() + childProject.getLastFailedBuild().getUrl(), builder.getEnvVars().get("RUN"));
    }
}
