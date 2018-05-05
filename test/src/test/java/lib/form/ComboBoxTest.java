/*
 * The MIT License
 * 
 * Copyright (c) 2013 Software in the Public Interest
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
package lib.form;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.ComboBoxModel;

import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.HudsonTestCase.WebClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * @author John McNally
 */
public class ComboBoxTest extends HudsonTestCase {

    /**
     * Used in testCompoundFieldDependentCombobox for Issue("JENKINS-16719")
     */
    public static class CompoundFieldComboBoxBuilder extends Publisher {
        private CompoundField compoundField;
        private String foo;
        
        @DataBoundConstructor
        public CompoundFieldComboBoxBuilder(CompoundField compoundField, String foo) {
            this.compoundField = compoundField;
            this.foo = foo;
        }
        
        @Extension
        public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
            public boolean isApplicable(Class jobType) {
                return true;
            }

            public ComboBoxModel doFillFooItems(
                    @QueryParameter @RelativePath("compoundField") String abc, 
                    @QueryParameter @RelativePath("compoundField") String xyz) {
                if (abc == null || xyz == null) {
                    throw new Error("doFillFooItems is broken");
                }
                return new ComboBoxModel(abc, xyz);
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
     * Used in testCompoundFieldDependentCombobox for Issue("JENKINS-16719")
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
        public static final class DescriptorImpl extends Descriptor<CompoundField> {}
    }

    /**
     * Confirms that relative paths work when prefilling a combobox text field
     */
    @Issue("JENKINS-16719")
    public void testCompoundFieldDependentComboBox() throws Exception {
        Descriptor d1 = new CompoundFieldComboBoxBuilder.DescriptorImpl();
        Publisher.all().add(d1);
        Descriptor d2 = new CompoundField.DescriptorImpl();
        Publisher.all().add(d2);
        FreeStyleProject p = createFreeStyleProject();
        p.getPublishersList().add(new CompoundFieldComboBoxBuilder(new CompoundField("AABBCC", "XXYYZZ"), null));
        try {
            new WebClient().getPage(p,"configure");
            
        } catch(AssertionError e) {
            if(e.getMessage().contains("doFillFooItems is broken")) {
                fail("Nested field values required for prefill were null");
            } else {
                throw e;
            }
        } finally {
            Publisher.all().remove(d1);
            Publisher.all().remove(d2);
        }
    }
}
