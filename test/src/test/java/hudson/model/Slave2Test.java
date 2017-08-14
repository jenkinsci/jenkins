/*
 * The MIT License
 *
 * Copyright (c) 2015 CloudBees, Inc.
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

import hudson.DescriptorExtensionList;
import hudson.ExtensionList;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.RetentionStrategy;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeThat;

import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TestExtension;

/**
 * Tests for the {@link Slave} class.
 * There is also a Groovy implementation of such test file, hence the class name 
 * has an index.
 * @author Oleg Nenashev
 */
public class Slave2Test {
    
    @Rule
    public JenkinsRule rule = new JenkinsRule();
    
    @Test
    @Issue("SECURITY-195")
    public void shouldNotEscapeJnlpSlavesResources() throws Exception {
        Slave slave = rule.createSlave();
        
        // Spot-check correct requests
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
    
    private void assertJnlpJarUrlFails(@Nonnull Slave slave, @Nonnull String url) throws Exception {
        // Raw access to API
        Slave.JnlpJar jnlpJar = slave.getComputer().getJnlpJars(url);
        try {
            jnlpJar.getURL();
        } catch (MalformedURLException ex) {
            // we expect the exception here
            return;
        }
        fail("Expected the MalformedURLException for " + url);
    }
    
    private void assertJnlpJarUrlIsAllowed(@Nonnull Slave slave, @Nonnull String url) throws Exception {
        // Raw access to API
        Slave.JnlpJar jnlpJar = slave.getComputer().getJnlpJars(url);
        assertNotNull(jnlpJar.getURL());
 
        
        // Access from a Web client
        JenkinsRule.WebClient client = rule.createWebClient();
        assertEquals(200, client.getPage(client.getContextPath() + "jnlpJars/" + URLEncoder.encode(url, "UTF-8")).getWebResponse().getStatusCode());
        assertEquals(200, client.getPage(jnlpJar.getURL()).getWebResponse().getStatusCode());
    }

    @Test
    @Issue("JENKINS-36280")
    public void launcherFiltering() throws Exception {
        DumbSlave.DescriptorImpl descriptor =
                rule.getInstance().getDescriptorByType(DumbSlave.DescriptorImpl.class);
        DescriptorExtensionList<ComputerLauncher, Descriptor<ComputerLauncher>> descriptors =
                rule.getInstance().getDescriptorList(ComputerLauncher.class);
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
    public void retentionFiltering() throws Exception {
        DumbSlave.DescriptorImpl descriptor =
                rule.getInstance().getDescriptorByType(DumbSlave.DescriptorImpl.class);
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
    public void propertyFiltering() throws Exception {
        DumbSlave.DescriptorImpl descriptor =
                rule.getInstance().getDescriptorByType(DumbSlave.DescriptorImpl.class);
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
        public boolean filterType(@Nonnull Class<?> contextClass, @Nonnull Descriptor descriptor) {
            return !descriptors.contains(descriptor);
        }

        @Override
        public boolean filter(@CheckForNull Object context, @Nonnull Descriptor descriptor) {
            return !descriptors.contains(descriptor);
        }
    }
}
