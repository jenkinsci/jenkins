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
package hudson.util;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
//import hudson.util.FormFieldValidatorTest.BrokenFormValidatorBuilder.DescriptorImpl;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.HudsonTestCase.WebClient;
import org.jvnet.hudson.test.recipes.WithPlugin;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 * @author Kohsuke Kawaguchi
 */
public class FormFieldValidatorTest extends HudsonTestCase {
    @Bug(2771)
    @WithPlugin("tasks.jpi")
    public void test2771() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        new WebClient().getPage(p,"configure");
    }

    /**
     * Used in testNegative for Bug(3382)
     */
    public static class BrokenFormValidatorBuilder extends Publisher {
        public static final class DescriptorImpl extends BuildStepDescriptor {
            public boolean isApplicable(Class jobType) {
                return true;
            }

            public void doCheckXyz() {
                throw new Error("doCheckXyz is broken");
            }

            public String getDisplayName() {
                return "I have broken form field validation";
            }
        }

        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.BUILD;
        }
    }

    /**
     * Make sure that the validation methods are really called by testing a negative case.
     */
    @Bug(3382)
    public void testNegative() throws Exception {
        Descriptor d = new BrokenFormValidatorBuilder.DescriptorImpl();
        Publisher.all().add(d);
        try {
            FreeStyleProject p = createFreeStyleProject();
            p.getPublishersList().add(new BrokenFormValidatorBuilder());
            new WebClient().getPage(p,"configure");
            fail("should have failed");
        } catch(AssertionError e) {
            if(e.getMessage().contains("doCheckXyz is broken"))
                ; // expected
            else
                throw e;
        } finally {
            Publisher.all().remove(d);
        }
    }

    /**
     * Used in testOptionalCompoundFieldDependentValidation for Bug(16676)
     */
    public static class CompoundFieldValidatorBuilder extends Publisher {
        private CompoundField compoundField;
        private String foo;
        
        @DataBoundConstructor
        public CompoundFieldValidatorBuilder(CompoundField compoundField, String foo) {
            this.compoundField = compoundField;
            this.foo = foo;
        }
        
        @Extension
        public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
            public boolean isApplicable(Class jobType) {
                return true;
            }

            public void doCheckFoo(@QueryParameter boolean compoundField,
                    @QueryParameter @RelativePath("compoundField") String abc, 
                    @QueryParameter @RelativePath("compoundField") String xyz, String value) {
                if (compoundField && (abc == null || xyz == null)) {
                    throw new Error("doCheckFoo is broken");
                }
            }

            public String getDisplayName() {
                return "Compound Field form field validation";
            }
        }

        public CompoundField getCompoundField() {
            return compoundField;
        }


        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.BUILD;
        }
    }

    /**
     * Used in testOptionalCompoundFieldDependentValidation for Bug(16676)
     */
    public static class CompoundField extends AbstractDescribableImpl<CompoundField> {
        private final String abc;
        private final String xyz;

        @DataBoundConstructor
        public CompoundField(String abc, String xyz) {
            this.abc = abc;
            this.xyz = xyz;
        }

        public String getAbc() {
            return abc;
        }

        public String getXyz() {
            return xyz;
        }
        
        @Extension
        public static final class DescriptorImpl extends Descriptor<CompoundField> {
            public String getDisplayName() { return ""; }
        }
    }

    /**
     * Confirms that relative paths work in field validation
     */
    @Bug(16676)
    public void testOptionalCompoundFieldDependentValidation() throws Exception {
        Descriptor d1 = new CompoundFieldValidatorBuilder.DescriptorImpl();
        Publisher.all().add(d1);
        Descriptor d2 = new CompoundField.DescriptorImpl();
        Publisher.all().add(d2);
        FreeStyleProject p = createFreeStyleProject();
        p.getPublishersList().add(new CompoundFieldValidatorBuilder(new CompoundField("AABBCC", "XXYYZZ"), "FFOOOO"));
        try {
            new WebClient().getPage(p,"configure");
            
        } catch(AssertionError e) {
            if(e.getMessage().contains("doCheckFoo is broken")) {
                fail("Optional nested field values were null");
            } else {
                throw e;
            }
        } finally {
            Publisher.all().remove(d1);
            Publisher.all().remove(d2);
        }
    }
}
