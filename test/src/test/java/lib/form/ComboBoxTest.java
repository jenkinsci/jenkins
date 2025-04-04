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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.ComboBoxModel;
import jenkins.model.OptionalJobProperty;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlElementUtil;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * @author John McNally
 */
public class ComboBoxTest {

    @Rule public JenkinsRule j = new JenkinsRule();

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
            @Override
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


        @Override
        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.BUILD;
        }
    }

    /**
     * Used in testCompoundFieldDependentCombobox for Issue("JENKINS-16719")
     */
    public static class CompoundField implements Describable<CompoundField> {
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
    @Test
    public void testCompoundFieldDependentComboBox() throws Exception {
        Descriptor d1 = new CompoundFieldComboBoxBuilder.DescriptorImpl();
        Publisher.all().add(d1);
        Descriptor d2 = new CompoundField.DescriptorImpl();
        Publisher.all().add(d2);
        FreeStyleProject p = j.createFreeStyleProject();
        p.getPublishersList().add(new CompoundFieldComboBoxBuilder(new CompoundField("AABBCC", "XXYYZZ"), null));
        try {
            j.createWebClient().getPage(p, "configure");

        } catch (AssertionError e) {
            if (e.getMessage().contains("doFillFooItems is broken")) {
                fail("Nested field values required for prefill were null");
            } else {
                throw e;
            }
        } finally {
            Publisher.all().remove(d1);
            Publisher.all().remove(d2);
        }
    }

    public static class XssProperty extends OptionalJobProperty<Job<?, ?>> {
        @TestExtension("testEnsureXssNotPossible")
        public static class DescriptorImpl extends OptionalJobProperty.OptionalJobPropertyDescriptor {

            @NonNull
            @Override
            public String getDisplayName() {
                return "XSS Property";
            }

            public ComboBoxModel doFillXssItems() {
                return new ComboBoxModel("<h1>HACK</h1>");
            }
        }
    }

    @Issue("SECURITY-1525")
    @Test
    public void testEnsureXssNotPossible() throws Exception {
        XssProperty xssProperty = new XssProperty();
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(xssProperty);

        HtmlPage configurePage = j.createWebClient().getPage(p, "configure");
        int numberOfH1Before = configurePage.getElementsByTagName("h1").size();

        HtmlElement comboBox = configurePage.getElementByName("_.xss");
        HtmlElementUtil.click(comboBox);

        // no additional h1, meaning the "payload" is not interpreted
        int numberOfH1After = configurePage.getElementsByTagName("h1").size();

        assertEquals(numberOfH1Before, numberOfH1After);
    }
}
