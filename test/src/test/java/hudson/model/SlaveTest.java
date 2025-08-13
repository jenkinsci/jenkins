/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.util.XmlSlurper;
import hudson.DescriptorExtensionList;
import hudson.ExtensionList;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import hudson.security.csrf.CrumbIssuer;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.RetentionStrategy;
import hudson.util.FormValidation;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.htmlunit.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SlaveTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Makes sure that a form validation method gets inherited.
     */
    @Test
    void formValidation() throws Exception {
        j.executeOnServer(() -> {
            assertNotNull(j.jenkins.getDescriptor(DumbSlave.class).getCheckUrl("remoteFS"));
            return null;
        });
    }

    /**
     * Programmatic config.xml submission.
     */
    @Test
    void slaveConfigDotXml() throws Exception {
        DumbSlave s = j.createSlave();
        JenkinsRule.WebClient wc = j.createWebClient();
        Page p = wc.goTo("computer/" + s.getNodeName() + "/config.xml", "application/xml");
        String xml = p.getWebResponse().getContentAsString();
        new XmlSlurper().parseText(xml); // verify that it is XML

        // make sure it survives the roundtrip
        post("computer/" + s.getNodeName() + "/config.xml", xml);

        assertNotNull(j.jenkins.getNode(s.getNodeName()));

        xml = IOUtils.toString(getClass().getResource("SlaveTest/slave.xml").openStream());
        xml = xml.replace("NAME", s.getNodeName());
        post("computer/" + s.getNodeName() + "/config.xml", xml);

        s = (DumbSlave) j.jenkins.getNode(s.getNodeName());
        assertNotNull(s);
        assertEquals("some text", s.getNodeDescription());
        assertEquals(JNLPLauncher.class, s.getLauncher().getClass());
    }

    private void post(String url, String xml) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(j.getURL(), url).openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/xml;charset=UTF-8");
        con.setRequestProperty(CrumbIssuer.DEFAULT_CRUMB_NAME, "test");
        con.setDoOutput(true);
        con.getOutputStream().write(xml.getBytes(StandardCharsets.UTF_8));
        con.getOutputStream().close();
        IOUtils.copy(con.getInputStream(), System.out);
    }

    @Test
    void remoteFsCheck() throws Exception {
        DumbSlave.DescriptorImpl d = j.jenkins.getDescriptorByType(DumbSlave.DescriptorImpl.class);
        assertEquals(FormValidation.ok(), d.doCheckRemoteFS("c:\\"));
        assertEquals(FormValidation.ok(), d.doCheckRemoteFS("/tmp"));
        assertEquals(FormValidation.Kind.WARNING, d.doCheckRemoteFS("relative/path").kind);
        assertEquals(FormValidation.Kind.WARNING, d.doCheckRemoteFS("/net/foo/bar/zot").kind);
        assertEquals(FormValidation.Kind.WARNING, d.doCheckRemoteFS("\\\\machine\\folder\\foo").kind);
    }

    @Test
    @Issue("SECURITY-195")
    void shouldNotEscapeJnlpSlavesResources() throws Exception {
        Slave slave = j.createSlave();

        // Spot-check correct requests
        assertJnlpJarUrlIsAllowed(slave, "agent.jar");
        assertJnlpJarUrlIsAllowed(slave, "slave.jar");
        assertJnlpJarUrlIsAllowed(slave, "remoting.jar");
        assertJnlpJarUrlIsAllowed(slave, "jenkins-cli.jar");
        assertJnlpJarUrlIsAllowed(slave, "hudson-cli.jar");

        // Check that requests to other WEB-INF contents fail
        assertJnlpJarUrlFails(slave, "web.xml");
        assertJnlpJarUrlFails(slave, "web.xml");
        assertJnlpJarUrlFails(slave, "classes/bundled-plugins.txt");
        assertJnlpJarUrlFails(slave, "classes/dependencies.txt");
        assertJnlpJarUrlFails(slave, "plugins/ant.hpi");
        assertJnlpJarUrlFails(slave, "nonexistentfolder/something.txt");

        // Try various kinds of folder escaping (SECURITY-195)
        assertJnlpJarUrlFails(slave, "../");
        assertJnlpJarUrlFails(slave, "..");
        assertJnlpJarUrlFails(slave, "..\\");
        assertJnlpJarUrlFails(slave, "../foo/bar");
        assertJnlpJarUrlFails(slave, "..\\foo\\bar");
        assertJnlpJarUrlFails(slave, "foo/../../bar");
        assertJnlpJarUrlFails(slave, "./../foo/bar");
    }

    private void assertJnlpJarUrlFails(@NonNull Slave slave, @NonNull String url) {
        // Raw access to API
        Slave.JnlpJar jnlpJar = slave.getComputer().getJnlpJars(url);
        assertThrows(MalformedURLException.class, jnlpJar::getURL);
    }

    private void assertJnlpJarUrlIsAllowed(@NonNull Slave slave, @NonNull String url) throws Exception {
        // Raw access to API
        Slave.JnlpJar jnlpJar = slave.getComputer().getJnlpJars(url);
        assertNotNull(jnlpJar.getURL());

        // Access from a Web client
        JenkinsRule.WebClient client = j.createWebClient();
        assertEquals(200, client.getPage(client.getContextPath() + "jnlpJars/" + URLEncoder.encode(url, StandardCharsets.UTF_8)).getWebResponse().getStatusCode());
        assertEquals(200, client.getPage(jnlpJar.getURL()).getWebResponse().getStatusCode());
    }

    @Test
    @Issue("JENKINS-36280")
    void launcherFiltering() {
        DumbSlave.DescriptorImpl descriptor =
                j.getInstance().getDescriptorByType(DumbSlave.DescriptorImpl.class);
        DescriptorExtensionList<ComputerLauncher, Descriptor<ComputerLauncher>> descriptors =
                j.getInstance().getDescriptorList(ComputerLauncher.class);
        assumeTrue(descriptors.size() > 1, "we need at least two launchers to test this");
        assertThat(descriptor.computerLauncherDescriptors(null), containsInAnyOrder(descriptors.toArray(new Descriptor[0])));

        Descriptor<ComputerLauncher> victim = descriptors.iterator().next();
        assertThat(descriptor.computerLauncherDescriptors(null), hasItem(victim));
        DynamicFilter.descriptors().add(victim);
        assertThat(descriptor.computerLauncherDescriptors(null), not(hasItem(victim)));
        DynamicFilter.descriptors().remove(victim);
        assertThat(descriptor.computerLauncherDescriptors(null), hasItem(victim));
    }

    @Test
    @Issue("JENKINS-36280")
    void retentionFiltering() {
        DumbSlave.DescriptorImpl descriptor =
                j.getInstance().getDescriptorByType(DumbSlave.DescriptorImpl.class);
        DescriptorExtensionList<RetentionStrategy<?>, Descriptor<RetentionStrategy<?>>> descriptors = RetentionStrategy.all();
        assumeTrue(descriptors.size() > 1, "we need at least two retention strategies to test this");
        assertThat(descriptor.retentionStrategyDescriptors(null), containsInAnyOrder(descriptors.toArray(new Descriptor[0])));

        Descriptor<RetentionStrategy<?>> victim = descriptors.iterator().next();
        assertThat(descriptor.retentionStrategyDescriptors(null), hasItem(victim));
        DynamicFilter.descriptors().add(victim);
        assertThat(descriptor.retentionStrategyDescriptors(null), not(hasItem(victim)));
        DynamicFilter.descriptors().remove(victim);
        assertThat(descriptor.retentionStrategyDescriptors(null), hasItem(victim));
    }

    @Test
    @Issue("JENKINS-36280")
    void propertyFiltering() {
        j.jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy()); // otherwise node descriptor is not available
        DumbSlave.DescriptorImpl descriptor =
                j.getInstance().getDescriptorByType(DumbSlave.DescriptorImpl.class);
        DescriptorExtensionList<NodeProperty<?>, NodePropertyDescriptor> descriptors = NodeProperty.all();
        assumeTrue(descriptors.size() > 1, "we need at least two node properties to test this");
        assertThat(descriptor.nodePropertyDescriptors(null), containsInAnyOrder(descriptors.toArray(new Descriptor[0])));

        NodePropertyDescriptor victim = descriptors.iterator().next();
        assertThat(descriptor.nodePropertyDescriptors(null), hasItem(victim));
        DynamicFilter.descriptors().add(victim);
        assertThat(descriptor.nodePropertyDescriptors(null), not(hasItem(victim)));
        DynamicFilter.descriptors().remove(victim);
        assertThat(descriptor.nodePropertyDescriptors(null), hasItem(victim));
    }

    @TestExtension
    public static class DynamicFilter extends DescriptorVisibilityFilter {

        private final Set<Descriptor> descriptors = new HashSet<>();

        public static Set<Descriptor> descriptors() {
            return ExtensionList.lookup(DescriptorVisibilityFilter.class).get(DynamicFilter.class).descriptors;
        }

        @Override
        public boolean filterType(@NonNull Class<?> contextClass, @NonNull Descriptor descriptor) {
            return !descriptors.contains(descriptor);
        }

        @Override
        public boolean filter(@CheckForNull Object context, @NonNull Descriptor descriptor) {
            return !descriptors.contains(descriptor);
        }
    }

}
