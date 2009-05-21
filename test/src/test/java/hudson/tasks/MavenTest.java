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
package hudson.tasks;

import hudson.model.Build;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.JDK;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.model.Cause.LegacyCodeCause;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.EnvironmentVariablesNodeProperty.Entry;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tasks.Maven.MavenInstaller;
import hudson.tasks.Maven.MavenInstallation.DescriptorImpl;
import hudson.tools.ToolProperty;
import hudson.tools.ToolPropertyDescriptor;
import hudson.tools.InstallSourceProperty;
import hudson.util.DescribableList;

import java.util.Collections;

import junit.framework.Assert;

import org.jvnet.hudson.test.HudsonTestCase;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlInput;

/**
 * @author Kohsuke Kawaguchi
 */
public class MavenTest extends HudsonTestCase {
    /**
     * Tests the round-tripping of the configuration.
     */
    public void testConfigRoundtrip() throws Exception {
        hudson.getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(); // reset

        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new Maven("a", null, "b.pom", "c=d", "-e"));

        WebClient webClient = new WebClient();
        HtmlPage page = webClient.getPage(p, "configure");

        HtmlForm form = page.getFormByName("config");
        submit(form);

        Maven m = p.getBuildersList().get(Maven.class);
        assertNotNull(m);
        assertEquals("a", m.targets);
        assertNull("found " + m.mavenName, m.mavenName);
        assertEquals("b.pom", m.pom);
        assertEquals("c=d", m.properties);
        assertEquals("-e", m.jvmOptions);
    }

    public void testWithNodeProperty() throws Exception {
        MavenInstallation maven = configureDefaultMaven();
        String mavenHome = maven.getHome();
        String mavenHomeVar = "${VAR_MAVEN}" + mavenHome.substring(3);
        String mavenVar = mavenHome.substring(0, 3);
        MavenInstallation varMaven = new MavenInstallation("varMaven", mavenHomeVar, NO_PROPERTIES);
        hudson.getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(maven, varMaven);

        JDK jdk = hudson.getJDK("default");
        String javaHome = jdk.getHome();
        String javaHomeVar = "${VAR_JAVA}" + javaHome.substring(3);
        String javaVar = javaHome.substring(0, 3);
        JDK varJDK = new JDK("varJDK", javaHomeVar);
        hudson.getJDKs().add(varJDK);
        Hudson.getInstance().getNodeProperties().replaceBy(
                Collections.singleton(new EnvironmentVariablesNodeProperty(
                        new Entry("VAR_MAVEN", mavenVar), new Entry("VAR_JAVA",
                                javaVar))));

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new Maven("--help", varMaven.getName()));
        project.setJDK(varJDK);

        Build<?, ?> build = project.scheduleBuild2(0).get();

        Assert.assertEquals(Result.SUCCESS, build.getResult());

    }

    public void testWithParameter() throws Exception {
        MavenInstallation maven = configureDefaultMaven();
        String mavenHome = maven.getHome();
        String mavenHomeVar = "${VAR_MAVEN}" + mavenHome.substring(3);
        String mavenVar = mavenHome.substring(0, 3);
        MavenInstallation varMaven = new MavenInstallation("varMaven",mavenHomeVar,NO_PROPERTIES);
        hudson.getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(maven, varMaven);

        JDK jdk = hudson.getJDK("default");
        String javaHome = jdk.getHome();
        String javaHomeVar = "${VAR_JAVA}" + javaHome.substring(3);
        String javaVar = javaHome.substring(0, 3);
        JDK varJDK = new JDK("varJDK", javaHomeVar);
        hudson.getJDKs().add(varJDK);

        FreeStyleProject project = createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("VAR_MAVEN", "XXX"),
                new StringParameterDefinition("VAR_JAVA", "XXX")));
        project.getBuildersList().add(new Maven("--help", varMaven.getName()));
        project.setJDK(varJDK);

        Build build = project.scheduleBuild2(0, new LegacyCodeCause(),
                new ParametersAction(
                        new StringParameterValue("VAR_MAVEN", mavenVar),
                        new StringParameterValue("VAR_JAVA", javaVar))).get();

        assertBuildStatusSuccess(build);

    }

    /**
     * Simulates the addition of the new Maven via UI and makes sure it works.
     */
    public void testGlobalConfigAjax() throws Exception {
        HtmlPage p = new WebClient().goTo("configure");
        HtmlForm f = p.getFormByName("config");
        HtmlButton b = getButtonByCaption(f, "Add Maven");
        b.click();
        findPreviousInputElement(b,"name").setValueAttribute("myMaven");
        findPreviousInputElement(b,"home").setValueAttribute("/tmp/foo");
        submit(f);
        verify();

        // another submission and verfify it survives a roundtrip
        p = new WebClient().goTo("configure");
        f = p.getFormByName("config");
        submit(f);
        verify();
    }

    private void verify() throws Exception {
        MavenInstallation[] l = get(DescriptorImpl.class).getInstallations();
        assertEquals(1,l.length);
        assertEqualBeans(l[0],new MavenInstallation("myMaven","/tmp/foo",NO_PROPERTIES),"name,home");

        // by default we should get the auto installer
        DescribableList<ToolProperty<?>,ToolPropertyDescriptor> props = l[0].getProperties();
        assertEquals(1,props.size());
        InstallSourceProperty isp = props.get(InstallSourceProperty.class);
        assertEquals(1,isp.installers.size());
        assertNotNull(isp.installers.get(MavenInstaller.class));
    }
}
