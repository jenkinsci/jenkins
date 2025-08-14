/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.common.collect.LinkedListMultimap;
import com.thoughtworks.xstream.XStream;
import hudson.ExtensionList;
import hudson.Launcher;
import hudson.XmlFile;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Saveable;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.XStream2;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import jenkins.model.GlobalConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ClassFilterImplTest {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void controllerToAgentBypassesWhitelist() throws Exception {
        assumeTrue(ClassFilterImpl.WHITELISTED_CLASSES.stream().noneMatch(clazz -> clazz.equals(LinkedListMultimap.class.getName())));
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedNode(r.createSlave());
        p.getBuildersList().add(new M2SBuilder());
        r.assertLogContains("sent {}", r.buildAndAssertSuccess(p));
    }

    public static class M2SBuilder extends Builder {
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            listener.getLogger().println("sent " + launcher.getChannel().call(new M2S()));
            return true;
        }

        @TestExtension("controllerToAgentBypassesWhitelist")
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
            @SuppressWarnings("rawtypes")
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }
        }
    }

    private static class M2S extends MasterToSlaveCallable<String, RuntimeException> {
        private final LinkedListMultimap<?, ?> obj = LinkedListMultimap.create();

        @Override
        public String call() throws RuntimeException {
            return obj.toString();
        }
    }

    // Note that currently even M2S callables are rejected when using classes blacklisted in ClassFilter.STANDARD, such as JSONObject.

    @Test
    void agentToControllerRequiresWhitelist() throws Exception {
        assumeTrue(ClassFilterImpl.WHITELISTED_CLASSES.stream().noneMatch(clazz -> clazz.equals(LinkedListMultimap.class.getName())));
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedNode(r.createSlave());
        p.getBuildersList().add(new S2MBuilder());
        r.buildAndAssertStatus(Result.FAILURE, p);
    }

    public static class S2MBuilder extends Builder {
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            listener.getLogger().println("received " + launcher.getChannel().call(new S2M()));
            return true;
        }

        @TestExtension("agentToControllerRequiresWhitelist")
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
            @SuppressWarnings("rawtypes")
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }
        }
    }

    private static class S2M extends MasterToSlaveCallable<LinkedListMultimap<?, ?>, RuntimeException> {
        @Override
        public LinkedListMultimap<?, ?> call() throws RuntimeException {
            return LinkedListMultimap.create();
        }
    }

    @Test
    void xstreamRequiresWhitelist() throws Exception {
        assumeTrue(ClassFilterImpl.WHITELISTED_CLASSES.stream().noneMatch(clazz -> clazz.equals(LinkedListMultimap.class.getName())));
        Config config = GlobalConfiguration.all().get(Config.class);
        config.save();
        config.obj = LinkedListMultimap.create();
        config.save();
        assertThat(config.getConfigFile().asString(), not(containsString("LinkedListMultimap")));
        config.unrelated = "modified";
        Files.writeString(config.getConfigFile().getFile().toPath(), new XStream(XStream2.getDefaultDriver()).toXML(config), StandardCharsets.UTF_8);
        assertThat(config.getConfigFile().asString(), allOf(containsString("LinkedListMultimap"), containsString("modified")));
        config.obj = null;
        config.unrelated = null;
        config.load();
        assertNull(config.obj);
        assertEquals("modified", config.unrelated);
        Map<Saveable, OldDataMonitor.VersionRange> data = ExtensionList.lookupSingleton(OldDataMonitor.class).getData();
        assertEquals(Set.of(config), data.keySet());
        assertThat(data.values().iterator().next().extra, allOf(containsString("LinkedListMultimap"), containsString("https://www.jenkins.io/redirect/class-filter/")));
    }

    @TestExtension("xstreamRequiresWhitelist")
    public static class Config extends GlobalConfiguration {
        LinkedListMultimap<?, ?> obj;
        String unrelated;

        @Override
        protected XmlFile getConfigFile() {
            return super.getConfigFile();
        }
    }

}
