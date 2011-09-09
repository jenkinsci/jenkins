/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc.
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
package hudson.tasks;

import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Functions;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixRun;
import hudson.matrix.MatrixProject;
import hudson.model.Cause.UserCause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.PasswordParameterDefinition;
import hudson.model.StringParameterDefinition;
import hudson.tasks.Ant.AntInstallation;
import hudson.tasks.Ant.AntInstallation.DescriptorImpl;
import hudson.tasks.Ant.AntInstaller;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolProperty;
import hudson.tools.ToolPropertyDescriptor;
import hudson.util.DescribableList;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SingleFileSCM;

/**
 * @author Kohsuke Kawaguchi
 */
public class AntTest extends HudsonTestCase {
    /**
     * Tests the round-tripping of the configuration.
     */
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new Ant("a",null,"-b","c.xml","d=e"));

        WebClient webClient = new WebClient();
        HtmlPage page = webClient.getPage(p,"configure");

        HtmlForm form = page.getFormByName("config");
        submit(form);

        Ant a = p.getBuildersList().get(Ant.class);
        assertNotNull(a);
        assertEquals("a",a.getTargets());
        assertNull(a.getAnt());
        assertEquals("-b",a.getAntOpts());
        assertEquals("c.xml",a.getBuildFile());
        assertEquals("d=e",a.getProperties());
    }

    /**
     * Simulates the addition of the new Ant via UI and makes sure it works.
     */
    public void testGlobalConfigAjax() throws Exception {
        HtmlPage p = new WebClient().goTo("configure");
        HtmlForm f = p.getFormByName("config");
        HtmlButton b = getButtonByCaption(f, "Add Ant");
        b.click();
        findPreviousInputElement(b,"name").setValueAttribute("myAnt");
        findPreviousInputElement(b,"home").setValueAttribute("/tmp/foo");
        submit(f);
        verify();

        // another submission and verify it survives a roundtrip
        p = new WebClient().goTo("configure");
        f = p.getFormByName("config");
        submit(f);
        verify();
    }

    private void verify() throws Exception {
        AntInstallation[] l = get(DescriptorImpl.class).getInstallations();
        assertEquals(1,l.length);
        assertEqualBeans(l[0],new AntInstallation("myAnt","/tmp/foo",NO_PROPERTIES),"name,home");

        // by default we should get the auto installer
        DescribableList<ToolProperty<?>,ToolPropertyDescriptor> props = l[0].getProperties();
        assertEquals(1,props.size());
        InstallSourceProperty isp = props.get(InstallSourceProperty.class);
        assertEquals(1,isp.installers.size());
        assertNotNull(isp.installers.get(AntInstaller.class));
    }

    public void testSensitiveParameters() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        ParametersDefinitionProperty pdb = new ParametersDefinitionProperty(
                new StringParameterDefinition("string", "defaultValue", "string description"),
                new PasswordParameterDefinition("password", "12345", "password description"),
                new StringParameterDefinition("string2", "Value2", "string description")
        );
        project.addProperty(pdb);
        project.setScm(new SingleFileSCM("build.xml", hudson.tasks._ant.AntTargetAnnotationTest.class.getResource("simple-build.xml")));

        project.getBuildersList().add(new Ant("foo",null,null,null,null));

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        String buildLog = getLog(build);
        assertNotNull(buildLog);
	System.out.println(buildLog);
        assertFalse(buildLog.contains("-Dpassword=12345"));
    }

    public void testParameterExpansion() throws Exception {
        String antName = configureDefaultAnt().getName();
        // *_URL vars are not set if hudson.getRootUrl() is null:
        ((Mailer.DescriptorImpl)hudson.getDescriptor(Mailer.class)).setHudsonUrl("http://test/");
        // Use a matrix project so we have env stuff via builtins, parameters and matrix axis.
        MatrixProject project = createMatrixProject("test project"); // Space in name
        project.setAxes(new AxisList(new Axis("AX", "is")));
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("FOO", "bar", "")));
        project.setScm(new ExtractResourceSCM(getClass().getResource("ant-job.zip")));
        project.getBuildersList().add(new Ant("", antName, null, null,
                "vNUM=$BUILD_NUMBER\nvID=$BUILD_ID\nvJOB=$JOB_NAME\nvTAG=$BUILD_TAG\nvEXEC=$EXECUTOR_NUMBER\n"
                + "vNODE=$NODE_NAME\nvLAB=$NODE_LABELS\nvJAV=$JAVA_HOME\nvWS=$WORKSPACE\nvHURL=$HUDSON_URL\n"
                + "vBURL=$BUILD_URL\nvJURL=$JOB_URL\nvHH=$HUDSON_HOME\nvJH=$JENKINS_HOME\nvFOO=$FOO\nvAX=$AX"));
        assertBuildStatusSuccess(project.scheduleBuild2(0, new UserCause()));
        MatrixRun build = project.getItem("AX=is").getLastBuild();
        String log = getLog(build);
        assertTrue("Missing $BUILD_NUMBER: " + log, log.contains("vNUM=1"));
        assertTrue("Missing $BUILD_ID: " + log, log.contains("vID=2")); // Assuming the year starts with 2!
        assertTrue("Missing $JOB_NAME: " + log, log.contains(project.getName()));
        // Odd build tag, but it's constructed with getParent().getName() and the parent is the
        // matrix configuration, not the project.. if matrix build tag ever changes, update
        // expected value here:
        assertTrue("Missing $BUILD_TAG: " + log, log.contains("vTAG=jenkins-AX=is-1"));
        assertTrue("Missing $EXECUTOR_NUMBER: " + log, log.matches("(?s).*vEXEC=\\d.*"));
        // $NODE_NAME is expected to be empty when running on master.. not checking.
        assertTrue("Missing $NODE_LABELS: " + log, log.contains("vLAB=master"));
        assertTrue("Missing $JAVA_HOME: " + log, log.matches("(?s).*vJH=[^\\r\\n].*"));
        assertTrue("Missing $WORKSPACE: " + log, log.matches("(?s).*vWS=[^\\r\\n].*"));
        assertTrue("Missing $HUDSON_URL: " + log, log.contains("vHURL=http"));
        assertTrue("Missing $BUILD_URL: " + log, log.contains("vBURL=http"));
        assertTrue("Missing $JOB_URL: " + log, log.contains("vJURL=http"));
        assertTrue("Missing $HUDSON_HOME: " + log, log.matches("(?s).*vHH=[^\\r\\n].*"));
        assertTrue("Missing $JENKINS_HOME: " + log, log.matches("(?s).*vJH=[^\\r\\n].*"));
        assertTrue("Missing build parameter $FOO: " + log, log.contains("vFOO=bar"));
        assertTrue("Missing matrix axis $AX: " + log, log.contains("vAX=is"));
    }

    public void testParameterExpansionByShell() throws Exception {
        String antName = configureDefaultAnt().getName();
        FreeStyleProject project = createFreeStyleProject();
        project.setScm(new ExtractResourceSCM(getClass().getResource("ant-job.zip")));
        String homeVar = Functions.isWindows() ? "%HOME%" : "$HOME";
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("vFOO", homeVar, ""),
                new StringParameterDefinition("vBAR", "Home sweet " + homeVar + ".", "")));
        project.getBuildersList().add(new Ant("", antName, null, null,
                "vHOME=" + homeVar + "\nvFOOHOME=Foo " + homeVar + "\n"));
        FreeStyleBuild build = project.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(build);
        String log = getLog(build);
        if (!Functions.isWindows()) homeVar = "\\" + homeVar; // Regex escape for $
        assertTrue("Missing simple HOME parameter: " + log,
                   log.matches("(?s).*vFOO=(?!" + homeVar + ").*"));
        assertTrue("Missing HOME parameter with other text: " + log,
                   log.matches("(?s).*vBAR=Home sweet (?!" + homeVar + ")[^\\r\\n]*\\..*"));
        assertTrue("Missing HOME ant property: " + log,
                   log.matches("(?s).*vHOME=(?!" + homeVar + ").*"));
        assertTrue("Missing HOME ant property with other text: " + log,
                   log.matches("(?s).*vFOOHOME=Foo (?!" + homeVar + ").*"));
    }

    @Bug(7108)
    public void testEscapeXmlInParameters() throws Exception {
        String antName = configureDefaultAnt().getName();
        FreeStyleProject project = createFreeStyleProject();
        project.setScm(new ExtractResourceSCM(getClass().getResource("ant-job.zip")));
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("vFOO", "<xml/>", "")));
        project.getBuildersList().add(new Ant("", antName, null, null, "vBAR=<xml/>\n"));
        FreeStyleBuild build = project.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(build);
        String log = getLog(build);
        assertTrue("Missing parameter: " + log, log.contains("vFOO=<xml/>"));
        assertTrue("Missing ant property: " + log, log.contains("vBAR=<xml/>"));
    }
}
