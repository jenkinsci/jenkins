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

import com.google.common.collect.LinkedListMultimap;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.stream.Collectors;
import jenkins.model.GlobalConfiguration;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.junit.Assume.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.WithoutJenkins;

public class ClassFilterImplTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule().record(ClassFilterImpl.class, Level.FINE);

    @WithoutJenkins
    @Test
    public void whitelistSanity() throws Exception {
        try (InputStream is = ClassFilterImpl.class.getResourceAsStream("whitelisted-classes.txt")) {
            List<String> lines = IOUtils.readLines(is, StandardCharsets.UTF_8).stream().filter(line -> !line.matches("#.*|\\s*")).collect(Collectors.toList());
            assertThat("whitelist is ordered", new TreeSet<>(lines), contains(lines.toArray(new String[0])));
            for (String line : lines) {
                try {
                    Class.forName(line);
                } catch (ClassNotFoundException x) {
                    System.err.println("skipping checks of unknown class " + line);
                }
            }
        }
    }

    @Test
    public void masterToSlaveBypassesWhitelist() throws Exception {
        assumeThat(ClassFilterImpl.WHITELISTED_CLASSES, not(contains(LinkedListMultimap.class.getName())));
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
        @TestExtension("masterToSlaveBypassesWhitelist")
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
    public void slaveToMasterRequiresWhitelist() throws Exception {
        assumeThat(ClassFilterImpl.WHITELISTED_CLASSES, not(contains(LinkedListMultimap.class.getName())));
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedNode(r.createSlave());
        p.getBuildersList().add(new S2MBuilder());
        r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
    }
    public static class S2MBuilder extends Builder {
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            listener.getLogger().println("received " + launcher.getChannel().call(new S2M()));
            return true;
        }
        @TestExtension("slaveToMasterRequiresWhitelist")
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
    public void xstreamRequiresWhitelist() throws Exception {
        assumeThat(ClassFilterImpl.WHITELISTED_CLASSES, not(contains(LinkedListMultimap.class.getName())));
        Config config = GlobalConfiguration.all().get(Config.class);
        config.save();
        config.obj = LinkedListMultimap.create();
        config.save();
        assertThat(config.xml(), not(containsString("LinkedListMultimap")));
    }
    @TestExtension("xstreamRequiresWhitelist")
    public static class Config extends GlobalConfiguration {
        LinkedListMultimap<?, ?> obj;
        String xml() throws IOException {
            return getConfigFile().asString();
        }
    }

}
