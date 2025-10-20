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

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.EnvVars;
import hudson.Functions;
import hudson.model.FreeStyleProject;
import hudson.model.JDK;
import hudson.model.labels.LabelAtom;
import hudson.slaves.DumbSlave;
import hudson.tasks.Ant.AntInstallation;
import hudson.tasks.BatchFile;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tasks.Shell;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * This class tests that environment variables from node properties are applied,
 * and that the priority is maintained: parameters > slave node properties >
 * master node properties
 */
@WithJenkins
class ToolLocationNodePropertyTest {

    private DumbSlave slave;
    private FreeStyleProject project;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;

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

    @Test
    void formRoundTrip() throws Exception {
        MavenInstallation.DescriptorImpl mavenDescriptor = j.jenkins.getDescriptorByType(MavenInstallation.DescriptorImpl.class);
        mavenDescriptor.setInstallations(new MavenInstallation("maven", "XXX", JenkinsRule.NO_PROPERTIES));
        AntInstallation.DescriptorImpl antDescriptor = j.jenkins.getDescriptorByType(AntInstallation.DescriptorImpl.class);
        antDescriptor.setInstallations(new AntInstallation("ant", "XXX", JenkinsRule.NO_PROPERTIES));
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

    private void configureDumpEnvBuilder() {
        if (Functions.isWindows())
            project.getBuildersList().add(new BatchFile("set"));
        else
            project.getBuildersList().add(new Shell("export"));
    }
}
