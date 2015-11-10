/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Tom Huybrechts
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
package hudson.tools;

import static org.junit.Assert.assertEquals;

import hudson.Functions;
import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.slaves.DumbSlave;
import hudson.tasks.Maven;
import hudson.tasks.BatchFile;
import hudson.tasks.Ant;
import hudson.tasks.Shell;
import hudson.tasks.Ant.AntInstallation;
import hudson.tasks.Maven.MavenInstallation;
import hudson.EnvVars;
import hudson.maven.MavenModuleSet;

import java.io.IOException;

import jenkins.model.Jenkins;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.ExtractResourceSCM;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 * This class tests that environment variables from node properties are applied,
 * and that the priority is maintained: parameters > slave node properties >
 * master node properties
 */
public class ToolLocationNodePropertyTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private DumbSlave slave;
    private FreeStyleProject project;

    @Test
    public void formRoundTrip() throws Exception {
        MavenInstallation.DescriptorImpl mavenDescriptor = j.jenkins.getDescriptorByType(MavenInstallation.DescriptorImpl.class);
        mavenDescriptor.setInstallations(new MavenInstallation("maven", "XXX", j.NO_PROPERTIES));
        AntInstallation.DescriptorImpl antDescriptor = j.jenkins.getDescriptorByType(AntInstallation.DescriptorImpl.class);
        antDescriptor.setInstallations(new AntInstallation("ant", "XXX", j.NO_PROPERTIES));
        JDK.DescriptorImpl jdkDescriptor = j.jenkins.getDescriptorByType(JDK.DescriptorImpl.class);
        jdkDescriptor.setInstallations(new JDK("jdk", "XXX"));

        ToolLocationNodeProperty property = new ToolLocationNodeProperty(
                new ToolLocationNodeProperty.ToolLocation(jdkDescriptor, "jdk", "foobar"),
                new ToolLocationNodeProperty.ToolLocation(mavenDescriptor, "maven", "barzot"),
                new ToolLocationNodeProperty.ToolLocation(antDescriptor, "ant", "zotfoo"));
        slave.getNodeProperties().add(property);

        WebClient webClient = j.createWebClient();
        HtmlPage page = webClient.getPage(slave, "configure");
        HtmlForm form = page.getFormByName("config");
        j.submit(form);

        assertEquals(1, slave.getNodeProperties().toList().size());

        ToolLocationNodeProperty prop = slave.getNodeProperties().get(ToolLocationNodeProperty.class);
        assertEquals(3, prop.getLocations().size());

        ToolLocationNodeProperty.ToolLocation location = prop.getLocations().get(0);
        assertEquals(jdkDescriptor, location.getType());
        assertEquals("jdk", location.getName());
        assertEquals("foobar", location.getHome());

        location = prop.getLocations().get(1);
        assertEquals(mavenDescriptor, location.getType());
        assertEquals("maven", location.getName());
        assertEquals("barzot", location.getHome());

        location = prop.getLocations().get(2);
        assertEquals(antDescriptor, location.getType());
        assertEquals("ant", location.getName());
        assertEquals("zotfoo", location.getHome());
    }

    @Test
    public void maven() throws Exception {
        MavenInstallation maven = j.configureDefaultMaven();
        String mavenPath = maven.getHome();
        Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(new MavenInstallation("maven", "THIS IS WRONG", j.NO_PROPERTIES));

        project.getBuildersList().add(new Maven("--version", "maven"));
        configureDumpEnvBuilder();

        Build build = project.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, build);

        ToolLocationNodeProperty property = new ToolLocationNodeProperty(
                new ToolLocationNodeProperty.ToolLocation(j.jenkins.getDescriptorByType(MavenInstallation.DescriptorImpl.class), "maven", mavenPath));
        slave.getNodeProperties().add(property);

        build = project.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.SUCCESS, build);
    }

    private void configureDumpEnvBuilder() throws IOException {
        if(Functions.isWindows())
            project.getBuildersList().add(new BatchFile("set"));
        else
            project.getBuildersList().add(new Shell("export"));
    }

    @Test
    public void ant() throws Exception {
        Ant.AntInstallation ant = j.configureDefaultAnt();
        String antPath = ant.getHome();
        Jenkins.getInstance().getDescriptorByType(Ant.DescriptorImpl.class).setInstallations(new AntInstallation("ant", "THIS IS WRONG"));

        project.setScm(new SingleFileSCM("build.xml", "<project name='foo'/>"));
        project.getBuildersList().add(new Ant("-version", "ant", null,null,null));
        configureDumpEnvBuilder();

        Build build = project.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, build);

        ToolLocationNodeProperty property = new ToolLocationNodeProperty(
                new ToolLocationNodeProperty.ToolLocation(j.jenkins.getDescriptorByType(AntInstallation.DescriptorImpl.class), "ant", antPath));
        slave.getNodeProperties().add(property);

        build = project.scheduleBuild2(0).get();
        System.out.println(build.getLog());
        j.assertBuildStatus(Result.SUCCESS, build);
    }

    @Test
    public void nativeMaven() throws Exception {
        MavenInstallation maven = j.configureDefaultMaven();
        String mavenPath = maven.getHome();
        Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(new MavenInstallation("maven", "THIS IS WRONG", j.NO_PROPERTIES));

        MavenModuleSet project = j.createMavenProject();
        project.setScm(new ExtractResourceSCM(getClass().getResource(
                "/simple-projects.zip")));
        project.setAssignedLabel(slave.getSelfLabel());
        project.setJDK(j.jenkins.getJDK("default"));

        project.setMaven("maven");
        project.setGoals("clean");

        Run build = project.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, build);

        ToolLocationNodeProperty property = new ToolLocationNodeProperty(
                new ToolLocationNodeProperty.ToolLocation(j.jenkins.getDescriptorByType(MavenInstallation.DescriptorImpl.class), "maven", mavenPath));
        slave.getNodeProperties().add(property);

        build = project.scheduleBuild2(0).get();
        System.out.println(build.getLog());
        j.assertBuildStatus(Result.SUCCESS, build);
    }

    @Before
    public void setUp() throws Exception {
        EnvVars env = new EnvVars();
        // we don't want Maven, Ant, etc. to be discovered in the path for this test to work,
        // but on Unix these tools rely on other basic Unix tools (like env) for its operation,
        // so empty path breaks the test.
        env.put("PATH", "/bin:/usr/bin");
        env.put("M2_HOME", "empty");
        slave = j.createSlave(new LabelAtom("slave"), env);
        project = j.createFreeStyleProject();
        project.setAssignedLabel(slave.getSelfLabel());
    }
}
