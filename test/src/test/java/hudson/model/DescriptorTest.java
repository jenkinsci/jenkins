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
import java.util.Arrays;
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
import org.kohsuke.stapler.DataBoundConstructor;
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
        @Override public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
    @TestExtension("overriddenId") public static final BuildStepDescriptor<Builder> builderA = new DescriptorImpl("builder-a");
    @TestExtension("overriddenId") public static final BuildStepDescriptor<Builder> builderB = new DescriptorImpl("builder-b");

    @Issue("JENKINS-28110")
    @Test public void nestedDescribableOverridingId() throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject("p");
        p.getBuildersList().add(new B1(Arrays.asList(new D1(), new D2())));
        rule.configRoundtrip(p);
        rule.assertLogContains("[D1, D2]", rule.buildAndAssertSuccess(p));
    }
    public static abstract class D extends AbstractDescribableImpl<D> {
        @Override public String toString() {return getDescriptor().getDisplayName();}
    }
    public static class D1 extends D {
        @DataBoundConstructor public D1() {}
        @TestExtension("nestedDescribableOverridingId") public static class DescriptorImpl extends Descriptor<D> {
            @Override public String getId() {return "D1-id";}
        }
    }
    public static class D2 extends D {
        @DataBoundConstructor public D2() {}
        @TestExtension("nestedDescribableOverridingId") public static class DescriptorImpl extends Descriptor<D> {
            @Override public String getId() {return "D2-id";}
        }
    }
    public static class B1 extends Builder {
        public final List<D> ds;
        @DataBoundConstructor public B1(List<D> ds) {
            this.ds = ds;
        }
        @Override public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            listener.getLogger().println(ds);
            return true;
        }
        @TestExtension("nestedDescribableOverridingId") public static class DescriptorImpl extends Descriptor<Builder> {}
    }

    @Ignore("never worked: TypePair.convertJSON looks for @DataBoundConstructor on D3 (Stapler does not grok Descriptor)")
    @Issue("JENKINS-28110")
    @Test public void nestedDescribableSharingClass() throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject("p");
        p.getBuildersList().add(new B2(Arrays.asList(new D3("d3a"), new D3("d3b"))));
        rule.configRoundtrip(p);
        rule.assertLogContains("[d3a, d3b]", rule.buildAndAssertSuccess(p));
    }
    public static class D3 implements Describable<D3> {
        private final String id;
        D3(String id) {
            this.id = id;
        }
        @Override public String toString() {
            return id;
        }
        @Override public Descriptor<D3> getDescriptor() {
            return Jenkins.getInstance().getDescriptorByName(id);
        }
    }
    public static class D3D extends Descriptor<D3> {
        private final String id;
        public D3D(String id) {
            super(D3.class);
            this.id = id;
        }
        @Override public String getId() {
            return id;
        }
        @Override public D3 newInstance(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
            return new D3(id);
        }
    }
    @TestExtension("nestedDescribableSharingClass") public static final Descriptor<D3> d3a = new D3D("d3a");
    @TestExtension("nestedDescribableSharingClass") public static final Descriptor<D3> d3b = new D3D("d3b");
    public static class B2 extends Builder {
        public final List<D3> ds;
        @DataBoundConstructor public B2(List<D3> ds) {
            this.ds = ds;
        }
        @Override public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            listener.getLogger().println(ds);
            return true;
        }
        @TestExtension("nestedDescribableSharingClass") public static class DescriptorImpl extends Descriptor<Builder> {}
    }

}
