/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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

import hudson.Launcher;
import hudson.model.Descriptor.PropertyType;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import java.io.IOException;
import java.util.List;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest;

@SuppressWarnings({"unchecked", "rawtypes"})
public class DescriptorTest {

    public @Rule JenkinsRule rule = new JenkinsRule();

    @Issue("JENKINS-12307")
    @Test public void getItemTypeDescriptorOrDie() throws Exception {
        Describable<?> instance = new Shell("echo hello");
        Descriptor<?> descriptor = instance.getDescriptor();
        PropertyType propertyType = descriptor.getPropertyType(instance, "command");
        try {
            propertyType.getItemTypeDescriptorOrDie();
            fail("not supposed to succeed");
        } catch (AssertionError x) {
            for (String text : new String[] {"hudson.tasks.CommandInterpreter", "getCommand", "java.lang.String", "collection"}) {
                assertTrue(text + " mentioned in " + x, x.toString().contains(text));
            }
        }
    }

    @Ignore("TODO currently fails: after first configRoundtrip, builders list is empty because in newInstancesFromHeteroList $class is BuilderImpl (like stapler-class), kind=builder-a is ignored, and so d is null")
    @Issue("JENKINS-26781")
    @Test public void overriddenId() throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject();
        p.getBuildersList().add(new BuilderImpl("builder-a"));
        rule.configRoundtrip(p);
        List<Builder> builders = p.getBuildersList();
        assertEquals(1, builders.size());
        assertEquals(BuilderImpl.class, builders.get(0).getClass());
        assertEquals("builder-a", ((BuilderImpl) builders.get(0)).id);
        rule.assertLogContains("running builder-a", rule.buildAndAssertSuccess(p));
        p.getBuildersList().replace(new BuilderImpl("builder-b"));
        rule.configRoundtrip(p);
        builders = p.getBuildersList();
        assertEquals(1, builders.size());
        assertEquals(BuilderImpl.class, builders.get(0).getClass());
        assertEquals("builder-b", ((BuilderImpl) builders.get(0)).id);
        rule.assertLogContains("running builder-b", rule.buildAndAssertSuccess(p));
    }
    private static final class BuilderImpl extends Builder {
        private final String id;
        BuilderImpl(String id) {
            this.id = id;
        }
        @Override public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            listener.getLogger().println("running " + getDescriptor().getId());
            return true;
        }
        @Override public Descriptor<Builder> getDescriptor() {
            return (Descriptor<Builder>) Jenkins.getInstance().getDescriptorByName(id);
        }
    }
    private static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private final String id;
        DescriptorImpl(String id) {
            super(BuilderImpl.class);
            this.id = id;
        }
        @Override public String getId() {
            return id;
        }
        @Override public Builder newInstance(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
            return new BuilderImpl(id);
        }
        @Override public String getDisplayName() {
            return id;
        }
        @Override public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
    @TestExtension("overriddenId") public static final BuildStepDescriptor<Builder> builderA = new DescriptorImpl("builder-a");
    @TestExtension("overriddenId") public static final BuildStepDescriptor<Builder> builderB = new DescriptorImpl("builder-b");

}
