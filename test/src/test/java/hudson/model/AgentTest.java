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
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeThat;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.util.XmlSlurper;
import hudson.DescriptorExtensionList;
import hudson.ExtensionList;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import hudson.security.csrf.CrumbIssuer;
import hudson.agents.ComputerLauncher;
import hudson.agents.DumbAgent;
import hudson.agents.JNLPLauncher;
import hudson.agents.NodeProperty;
import hudson.agents.NodePropertyDescriptor;
import hudson.agents.RetentionStrategy;
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
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class AgentTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Makes sure that a form validation method gets inherited.
     */
    @Test
    public void formValidation() throws Exception {
        j.executeOnServer(() -> {
            assertNotNull(j.jenkins.getDescriptor(DumbAgent.class).getCheckUrl("remoteFS"));
            return null;
        });
    }

    /**
     * Programmatic config.xml submission.
     */
    @Test
    public void agentConfigDotXml() throws Exception {
        DumbAgent s = j.createAgent();
        JenkinsRule.WebClient wc = j.createWebClient();
        Page p = wc.goTo("computer/" + s.getNodeName() + "/config.xml", "application/xml");
        String xml = p.getWebResponse().getContentAsString();
        new XmlSlurper().parseText(xml); // verify that it is XML

        // make sure it survives the roundtrip
        post("computer/" + s.getNodeName() + "/config.xml", xml);

        assertNotNull(j.jenkins.getNode(s.getNodeName()));

        xml = IOUtils.toString(getClass().getResource("AgentTest/agent.xml").openStream());
        xml = xml.replace("NAME", s.getNodeName());
        post("computer/" + s.getNodeName() + "/config.xml", xml);

        s = (DumbAgent) j.jenkins.getNode(s.getNodeName());
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
    public void remoteFsCheck() throws Exception {
        DumbAgent.DescriptorImpl d = j.jenkins.getDescriptorByType(DumbAgent.DescriptorImpl.class);
        assertEquals(FormValidation.ok(), d.doCheckRemoteFS("c:\\"));
        assertEquals(FormValidation.ok(), d.doCheckRemoteFS("/tmp"));
        assertEquals(FormValidation.Kind.WARNING, d.doCheckRemoteFS("relative/path").kind);
        assertEquals(FormValidation.Kind.WARNING, d.doCheckRemoteFS("/net/foo/bar/zot").kind);
        assertEquals(FormValidation.Kind.WARNING, d.doCheckRemoteFS("\\\\machine\\folder\\foo").kind);
    }

    @Test
    @Issue("SECURITY-195")
    public void shouldNotEscapeJnlpAgentsResources() throws Exception {
        Agent agent = j.createAgent();

        // Spot-check correct requests
        assertJnlpJarUrlIsAllowed(agent, "agent.jar");
        assertJnlpJarUrlIsAllowed(agent, "agent.jar");
        assertJnlpJarUrlIsAllowed(agent, "remoting.jar");
        assertJnlpJarUrlIsAllowed(agent, "jenkins-cli.jar");
        assertJnlpJarUrlIsAllowed(agent, "hudson-cli.jar");

        // Check that requests to other WEB-INF contents fail
        assertJnlpJarUrlFails(agent, "web.xml");
        assertJnlpJarUrlFails(agent, "web.xml");
        assertJnlpJarUrlFails(agent, "classes/bundled-plugins.txt");
        assertJnlpJarUrlFails(agent, "classes/dependencies.txt");
        assertJnlpJarUrlFails(agent, "plugins/ant.hpi");
        assertJnlpJarUrlFails(agent, "nonexistentfolder/something.txt");

        // Try various kinds of folder escaping (SECURITY-195)
        assertJnlpJarUrlFails(agent, "../");
        assertJnlpJarUrlFails(agent, "..");
        assertJnlpJarUrlFails(agent, "..\\");
        assertJnlpJarUrlFails(agent, "../foo/bar");
        assertJnlpJarUrlFails(agent, "..\\foo\\bar");
        assertJnlpJarUrlFails(agent, "foo/../../bar");
        assertJnlpJarUrlFails(agent, "./../foo/bar");
    }

    private void assertJnlpJarUrlFails(@NonNull Agent agent, @NonNull String url) throws Exception {
        // Raw access to API
        Agent.JnlpJar jnlpJar = agent.getComputer().getJnlpJars(url);
        assertThrows(MalformedURLException.class, jnlpJar::getURL);
    }

    private void assertJnlpJarUrlIsAllowed(@NonNull Agent agent, @NonNull String url) throws Exception {
        // Raw access to API
        Agent.JnlpJar jnlpJar = agent.getComputer().getJnlpJars(url);
        assertNotNull(jnlpJar.getURL());


        // Access from a Web client
        JenkinsRule.WebClient client = j.createWebClient();
        assertEquals(200, client.getPage(client.getContextPath() + "jnlpJars/" + URLEncoder.encode(url, StandardCharsets.UTF_8)).getWebResponse().getStatusCode());
        assertEquals(200, client.getPage(jnlpJar.getURL()).getWebResponse().getStatusCode());
    }

    @Test
    @Issue("JENKINS-36280")
    public void launcherFiltering() {
        DumbAgent.DescriptorImpl descriptor =
                j.getInstance().getDescriptorByType(DumbAgent.DescriptorImpl.class);
        DescriptorExtensionList<ComputerLauncher, Descriptor<ComputerLauncher>> descriptors =
                j.getInstance().getDescriptorList(ComputerLauncher.class);
        assumeThat("we need at least two launchers to test this", descriptors.size(), not(anyOf(is(0), is(1))));
        assertThat(descriptor.computerLauncherDescriptors(null), containsInAnyOrder(descriptors.toArray(new Descriptor[descriptors.size()])));

        Descriptor<ComputerLauncher> victim = descriptors.iterator().next();
        assertThat(descriptor.computerLauncherDescriptors(null), hasItem(victim));
        DynamicFilter.descriptors().add(victim);
        assertThat(descriptor.computerLauncherDescriptors(null), not(hasItem(victim)));
        DynamicFilter.descriptors().remove(victim);
        assertThat(descriptor.computerLauncherDescriptors(null), hasItem(victim));
    }

    @Test
    @Issue("JENKINS-36280")
    public void retentionFiltering() {
        DumbAgent.DescriptorImpl descriptor =
                j.getInstance().getDescriptorByType(DumbAgent.DescriptorImpl.class);
        DescriptorExtensionList<RetentionStrategy<?>, Descriptor<RetentionStrategy<?>>> descriptors = RetentionStrategy.all();
        assumeThat("we need at least two retention strategies to test this", descriptors.size(), not(anyOf(is(0), is(1))));
        assertThat(descriptor.retentionStrategyDescriptors(null), containsInAnyOrder(descriptors.toArray(new Descriptor[descriptors.size()])));

        Descriptor<RetentionStrategy<?>> victim = descriptors.iterator().next();
        assertThat(descriptor.retentionStrategyDescriptors(null), hasItem(victim));
        DynamicFilter.descriptors().add(victim);
        assertThat(descriptor.retentionStrategyDescriptors(null), not(hasItem(victim)));
        DynamicFilter.descriptors().remove(victim);
        assertThat(descriptor.retentionStrategyDescriptors(null), hasItem(victim));
    }

    @Test
    @Issue("JENKINS-36280")
    public void propertyFiltering() {
        j.jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy()); // otherwise node descriptor is not available
        DumbAgent.DescriptorImpl descriptor =
                j.getInstance().getDescriptorByType(DumbAgent.DescriptorImpl.class);
        DescriptorExtensionList<NodeProperty<?>, NodePropertyDescriptor> descriptors = NodeProperty.all();
        assumeThat("we need at least two node properties to test this", descriptors.size(), not(anyOf(is(0), is(1))));
        assertThat(descriptor.nodePropertyDescriptors(null), containsInAnyOrder(descriptors.toArray(new Descriptor[descriptors.size()])));

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
