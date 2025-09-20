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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.EnvVars;
import hudson.model.Cause.LegacyCodeCause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.JDK;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.PasswordParameterDefinition;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tasks.Maven.MavenInstaller;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolProperty;
import hudson.tools.ToolPropertyDescriptor;
import hudson.util.DescribableList;
import java.util.Collections;
import java.util.Set;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import jenkins.mvn.DefaultGlobalSettingsProvider;
import jenkins.mvn.DefaultSettingsProvider;
import jenkins.mvn.FilePathGlobalSettingsProvider;
import jenkins.mvn.FilePathSettingsProvider;
import jenkins.mvn.GlobalMavenConfig;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.ToolInstallations;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.jelly.JellyFacet;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class MavenTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Tests the round-tripping of the configuration.
     */
    @Test
    void configRoundtrip() throws Exception {
        j.jenkins.getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(); // reset

        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new Maven("a", null, "b.pom", "c=d", "-e", true));

        JenkinsRule.WebClient webClient = j.createWebClient();
        HtmlPage page = webClient.getPage(p, "configure");

        HtmlForm form = page.getFormByName("config");
        j.submit(form);

        Maven m = p.getBuildersList().get(Maven.class);
        assertNotNull(m);
        assertEquals("a", m.targets);
        assertNull(m.mavenName, "found " + m.mavenName);
        assertEquals("b.pom", m.pom);
        assertEquals("c=d", m.properties);
        assertEquals("-e", m.jvmOptions);
        assertTrue(m.usesPrivateRepository());
    }

    @Test
    void withNodeProperty() throws Exception {
        MavenInstallation maven = ToolInstallations.configureDefaultMaven();
        String mavenHome = maven.getHome();
        String mavenHomeVar = "${VAR_MAVEN}" + mavenHome.substring(3);
        String mavenVar = mavenHome.substring(0, 3);
        MavenInstallation varMaven = new MavenInstallation("varMaven", mavenHomeVar, JenkinsRule.NO_PROPERTIES);
        j.jenkins.getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(maven, varMaven);

        JDK jdk = j.jenkins.getJDK("default");
        String javaHome = jdk.getHome();
        String javaHomeVar = "${VAR_JAVA}" + javaHome.substring(3);
        String javaVar = javaHome.substring(0, 3);
        JDK varJDK = new JDK("varJDK", javaHomeVar);
        j.jenkins.getJDKs().add(varJDK);
        j.jenkins.getNodeProperties().replaceBy(
                Set.of(new EnvironmentVariablesNodeProperty(
                        new EnvironmentVariablesNodeProperty.Entry("VAR_MAVEN", mavenVar), new EnvironmentVariablesNodeProperty.Entry("VAR_JAVA",
                                javaVar))));

        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new Maven("--help", varMaven.getName()));
        project.setJDK(varJDK);

        j.buildAndAssertSuccess(project);
    }

    @Test
    void withParameter() throws Exception {
        MavenInstallation maven = ToolInstallations.configureDefaultMaven();
        String mavenHome = maven.getHome();
        String mavenHomeVar = "${VAR_MAVEN}" + mavenHome.substring(3);
        String mavenVar = mavenHome.substring(0, 3);
        MavenInstallation varMaven = new MavenInstallation("varMaven", mavenHomeVar, JenkinsRule.NO_PROPERTIES);
        j.jenkins.getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(maven, varMaven);

        JDK jdk = j.jenkins.getJDK("default");
        String javaHome = jdk.getHome();
        String javaHomeVar = "${VAR_JAVA}" + javaHome.substring(3);
        String javaVar = javaHome.substring(0, 3);
        JDK varJDK = new JDK("varJDK", javaHomeVar);
        j.jenkins.getJDKs().add(varJDK);

        FreeStyleProject project = j.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("VAR_MAVEN", "XXX"),
                new StringParameterDefinition("VAR_JAVA", "XXX")));
        project.getBuildersList().add(new Maven("--help", varMaven.getName()));
        project.setJDK(varJDK);

        FreeStyleBuild build = project.scheduleBuild2(0, new LegacyCodeCause(),
                new ParametersAction(
                        new StringParameterValue("VAR_MAVEN", mavenVar),
                        new StringParameterValue("VAR_JAVA", javaVar))).get();

        j.assertBuildStatusSuccess(build);

    }

    /**
     * Simulates the addition of the new Maven via UI and makes sure it works.
     */
    @Test
    void globalConfigAjax() throws Exception {
        HtmlPage p = j.createWebClient().goTo("configureTools");
        HtmlForm f = p.getFormByName("config");
        HtmlButton b = j.getButtonByCaption(f, "Add Maven");
        b.click();
        j.findPreviousInputElement(b, "name").setValue("myMaven");
        j.findPreviousInputElement(b, "home").setValue("/tmp/foo");
        j.submit(f);
        verify();

        // another submission and verify it survives a roundtrip
        p = j.createWebClient().goTo("configure");
        f = p.getFormByName("config");
        j.submit(f);
        verify();
    }

    private void verify() throws Exception {
        MavenInstallation[] l = j.get(MavenInstallation.DescriptorImpl.class).getInstallations();
        assertEquals(1, l.length);
        j.assertEqualBeans(l[0], new MavenInstallation("myMaven", "/tmp/foo", JenkinsRule.NO_PROPERTIES), "name,home");

        // by default we should get the auto installer
        DescribableList<ToolProperty<?>, ToolPropertyDescriptor> props = l[0].getProperties();
        assertEquals(1, props.size());
        InstallSourceProperty isp = props.get(InstallSourceProperty.class);
        assertEquals(1, isp.installers.size());
        assertNotNull(isp.installers.get(MavenInstaller.class));
    }

    @Test
    void sensitiveParameters() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        ParametersDefinitionProperty pdb = new ParametersDefinitionProperty(
                new StringParameterDefinition("string", "defaultValue", "string description"),
                new PasswordParameterDefinition("password", "12345", "password description"),
                new StringParameterDefinition("string2", "Value2", "string description")
        );
        project.addProperty(pdb);
        project.setScm(new ExtractResourceSCM(getClass().getResource("maven-empty.zip")));
        project.getBuildersList().add(new Maven("clean package", null));

        FreeStyleBuild build = j.waitForCompletion(project.scheduleBuild2(0).waitForStart());
        j.assertLogNotContains("-Dpassword=12345", build);
    }

    @Test
    void parametersReferencedFromPropertiesShouldRetainBackslashes() throws Exception {
        final String properties = "global.path=$GLOBAL_PATH\nmy.path=$PATH\\\\Dir";
        final StringParameterDefinition parameter = new StringParameterDefinition("PATH", "C:\\Windows");
        final EnvironmentVariablesNodeProperty.Entry envVar = new EnvironmentVariablesNodeProperty.Entry("GLOBAL_PATH", "D:\\Jenkins");

        FreeStyleProject project = j.createFreeStyleProject();
        // This test implements legacy behavior, when Build Variables are injected by default
        project.getBuildersList().add(new Maven("--help", null, null, properties, null,
                false, null, null, true));
        project.addProperty(new ParametersDefinitionProperty(parameter));
        j.jenkins.getNodeProperties().replaceBy(Set.of(
                new EnvironmentVariablesNodeProperty(envVar)
        ));

        FreeStyleBuild build = j.waitForCompletion(project.scheduleBuild2(0).waitForStart());
        j.assertLogContains("-Dmy.path=C:\\Windows\\Dir", build);
        j.assertLogContains("-Dglobal.path=D:\\Jenkins", build);
    }

    @Test
    void defaultSettingsProvider() throws Exception {
        {
            FreeStyleProject p = j.createFreeStyleProject();
            p.getBuildersList().add(new Maven("a", null, "a.pom", "c=d", "-e", true));

            Maven m = p.getBuildersList().get(Maven.class);
            assertNotNull(m);
            assertEquals(DefaultSettingsProvider.class, m.getSettings().getClass());
            assertEquals(DefaultGlobalSettingsProvider.class, m.getGlobalSettings().getClass());
        }

        {
            GlobalMavenConfig globalMavenConfig = GlobalMavenConfig.get();
            assertNotNull(globalMavenConfig, "No global Maven Config available");
            globalMavenConfig.setSettingsProvider(new FilePathSettingsProvider("/tmp/settings.xml"));
            globalMavenConfig.setGlobalSettingsProvider(new FilePathGlobalSettingsProvider("/tmp/global-settings.xml"));

            FreeStyleProject p = j.createFreeStyleProject();
            p.getBuildersList().add(new Maven("b", null, "b.pom", "c=d", "-e", true));

            Maven m = p.getBuildersList().get(Maven.class);
            assertEquals(FilePathSettingsProvider.class, m.getSettings().getClass());
            assertEquals("/tmp/settings.xml", ((FilePathSettingsProvider) m.getSettings()).getPath());
            assertEquals("/tmp/global-settings.xml", ((FilePathGlobalSettingsProvider) m.getGlobalSettings()).getPath());
        }
    }

    @Issue("JENKINS-18898")
    @Test
    void testNullHome() {
        EnvVars env = new EnvVars();
        new MavenInstallation("_", "", Collections.emptyList()).buildEnvVars(env);
        assertTrue(env.isEmpty());
    }

    @Issue("JENKINS-26684")
    @Test
    void specialCharsInBuildVariablesPassedAsProperties() throws Exception {
        MavenInstallation maven = ToolInstallations.configureMaven3();

        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new Maven("--help", maven.getName()));
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("tilde", "~"),
                new StringParameterDefinition("exclamation_mark", "!"),
                new StringParameterDefinition("at_sign", "@"),
                new StringParameterDefinition("sharp", "#"),
                new StringParameterDefinition("dollar", "$"),
                new StringParameterDefinition("percent", "%"),
                new StringParameterDefinition("circumflex", "^"),
                new StringParameterDefinition("ampersand", "&"),
                new StringParameterDefinition("asterix", "*"),
                new StringParameterDefinition("parentheses", "()"),
                new StringParameterDefinition("underscore", "_"),
                new StringParameterDefinition("plus", "+"),
                new StringParameterDefinition("braces", "{}"),
                new StringParameterDefinition("brackets", "[]"),
                new StringParameterDefinition("colon", ":"),
                new StringParameterDefinition("semicolon", ";"),
                new StringParameterDefinition("quote", "\""),
                new StringParameterDefinition("apostrophe", "'"),
                new StringParameterDefinition("backslash", "\\"),
                new StringParameterDefinition("pipe", "|"),
                new StringParameterDefinition("angle_brackets", "<>"),
                new StringParameterDefinition("comma", ","),
                new StringParameterDefinition("period", "."),
                new StringParameterDefinition("slash", "/"),
                new StringParameterDefinition("question_mark", "?"),
                new StringParameterDefinition("space", " ")
        ));

        FreeStyleBuild build = j.buildAndAssertSuccess(p);
    }

    @Test
    void doPassBuildVariablesOptionally() throws Exception {
        MavenInstallation maven = ToolInstallations.configureMaven3();

        FreeStyleProject p = j.createFreeStyleProject();
        p.updateByXml((Source) new StreamSource(getClass().getResourceAsStream("MavenTest/doPassBuildVariablesOptionally.xml")));
        FreeStyleBuild build = j.buildAndAssertSuccess(p);
        assertTrue(p.getBuildersList().get(Maven.class).isInjectBuildVariables(), "Build variables injection should be enabled by default when loading from XML");
        j.assertLogContains("-DNAME=VALUE", build);

        p.getBuildersList().clear();
        p.getBuildersList().add(new Maven("--help", maven.getName(), null, null, null, false, null, null, false/*do not inject*/));

        build = j.buildAndAssertSuccess(p);
        j.assertLogNotContains("-DNAME=VALUE", build);

        p.getBuildersList().clear();
        p.getBuildersList().add(new Maven("--help", maven.getName(), null, null, null, false, null, null, true/*do inject*/));

        build = j.buildAndAssertSuccess(p);
        j.assertLogContains("-DNAME=VALUE", build);

        assertFalse(new Maven("", "").isInjectBuildVariables(), "Build variables injection should be disabled by default");
    }

    @Test
    void doAlwaysPassProperties() throws Exception {
        MavenInstallation maven = ToolInstallations.configureMaven3();

        FreeStyleProject p = j.createFreeStyleProject();
        String properties = "TEST_PROP1=VAL1\nTEST_PROP2=VAL2";

        p.getBuildersList().add(new Maven("--help", maven.getName(), null, properties, null, false, null,
                null, false/*do not inject build variables*/));
        FreeStyleBuild build = j.buildAndAssertSuccess(p);
        j.assertLogContains("-DTEST_PROP1=VAL1", build);
        j.assertLogContains("-DTEST_PROP2=VAL2", build);

        p.getBuildersList().clear();
        p.getBuildersList().add(new Maven("--help", maven.getName(), null, properties, null, false, null,
                null, true/*do inject build variables*/));
        build = j.buildAndAssertSuccess(p);
        j.assertLogContains("-DTEST_PROP1=VAL1", build);
        j.assertLogContains("-DTEST_PROP2=VAL2", build);
    }

    @Issue("JENKINS-34138")
    @Test
    void checkMavenInstallationEquals() throws Exception {
        MavenInstallation maven = ToolInstallations.configureMaven3();
        MavenInstallation maven2 = ToolInstallations.configureMaven3();
        assertEquals(maven.hashCode(), maven2.hashCode());
        assertEquals(maven, maven2);
    }

    @Issue("JENKINS-34138")
    @Test
    void checkMavenInstallationNotEquals() throws Exception {
        MavenInstallation maven3 = ToolInstallations.configureMaven3();
        MavenInstallation maven2 = ToolInstallations.configureDefaultMaven();
        assertNotEquals(maven3.hashCode(), maven2.hashCode());
        assertNotEquals(maven3, maven2);
    }

    @Test
    @Issue("JENKINS-65288")
    void submitPossibleWithoutJellyTrace() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage htmlPage = wc.goTo("configureTools");
        HtmlForm configForm = htmlPage.getFormByName("config");
        j.assertGoodStatus(j.submit(configForm));
    }

    /**
     * Ensure the form is still working when using {@link org.kohsuke.stapler.jelly.JellyFacet#TRACE}=true
     */
    @Test
    @Issue("JENKINS-65288")
    void submitPossibleWithJellyTrace() throws Exception {
        boolean currentValue = JellyFacet.TRACE;
        try {
            JellyFacet.TRACE = true;

            JenkinsRule.WebClient wc = j.createWebClient();
            HtmlPage htmlPage = wc.goTo("configureTools");
            HtmlForm configForm = htmlPage.getFormByName("config");
            j.assertGoodStatus(j.submit(configForm));
        } finally {
            JellyFacet.TRACE = currentValue;
        }
    }
}
