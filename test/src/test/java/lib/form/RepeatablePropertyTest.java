/*
 * The MIT License
 *
 * Copyright (C) 2010-2011 by Anthony Robinson
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTextInput;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class RepeatablePropertyTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    private static final String VIEW_WITHOUT_DEFAULT = "noDefault";
    private static final String VIEW_WITH_DEFAULT = "withDefault";

    private RootActionImpl rootAction;

    @Before
    public void setUp() {
        rootAction = ExtensionList.lookupSingleton(RootActionImpl.class);
    }

    @Test
    public void testSimple() throws Exception {
        rootAction.testRepeatable = createRepeatable();
        assertFormContents(VIEW_WITHOUT_DEFAULT, rootAction.testRepeatable);
    }

    @Test
    public void testNullFieldNoDefault() throws Exception {
        assertFormContents(VIEW_WITHOUT_DEFAULT, new ArrayList<>());
    }

    @Test
    public void testNullFieldWithDefault() throws Exception {
        rootAction.defaults = createRepeatable();
        assertFormContents(VIEW_WITH_DEFAULT, rootAction.defaults);
    }

    @Test
    public void testFieldNotNullWithDefaultIgnoresDefaults() throws Exception {
        rootAction.testRepeatable = createRepeatable();
        rootAction.defaults = new ArrayList<>(Arrays.asList(
           new ExcitingObject("This default should be ignored"),
           new ExcitingObject("Ignore me too")
        ));
        assertFormContents(VIEW_WITH_DEFAULT, rootAction.testRepeatable);
    }

    @Issue("JENKINS-37599")
    @Test
    public void testNestedRepeatableProperty() throws Exception {
        rootAction.testRepeatableContainer = Collections.emptyList();
        // minimum="1" is set for the upper one,
        // the form should be:
        // * 1 ExcitingObjectCotainer
        // * no ExcitingObject
        final HtmlForm form = getForm("nested");
        List<HtmlTextInput> containerNameInputs =
                form.getElementsByAttribute("input", "type", "text").stream()
                        .map(HtmlTextInput.class::cast)
                        .filter(input -> input.getNameAttribute().endsWith(".containerName"))
                        .collect(Collectors.toList());
        List<HtmlTextInput> greatPropertyInputs =
                form.getElementsByAttribute("input", "type", "text").stream()
                        .map(HtmlTextInput.class::cast)
                        .filter(input -> input.getNameAttribute().endsWith(".greatProperty"))
                        .collect(Collectors.toList());
        assertEquals(1, containerNameInputs.size());
        assertEquals(0, greatPropertyInputs.size());
    }

    private void assertFormContents(final String viewName, final ArrayList<ExcitingObject> expected) throws Exception {
        final HtmlForm form = getForm(viewName);
        final List<HtmlTextInput> inputs = toTextInputList(form.getElementsByAttribute("input", "type", "text"));
        assertEquals("size", expected.size(), inputs.size());
        for (int i = 0; i < expected.size(); i++)
            assertEquals(expected.get(i).greatProperty, inputs.get(i).getValue());
    }

    private List<HtmlTextInput> toTextInputList(final List<HtmlElement> inputs) {
        assertNotNull(inputs);
        final List<HtmlTextInput> textInputList = new ArrayList<>();
        for (HtmlElement input : inputs) {
            assertThat(input, instanceOf(HtmlTextInput.class));
            textInputList.add((HtmlTextInput) input);
        }
        return textInputList;
    }

    private ArrayList<ExcitingObject> createRepeatable() {
        return new ArrayList<>(Arrays.asList(
           new ExcitingObject("A nice thing"),
           new ExcitingObject("I'm even better"),
           new ExcitingObject("Don't bother, I'm not exciting at all")
        ));
    }

    private HtmlForm getForm(final String viewName) throws Exception {
        final HtmlPage page = j.createWebClient().goTo("self/" + viewName);
        final HtmlForm form = page.getFormByName("config");
        return form;
    }

    public static final class ExcitingObject implements Describable<ExcitingObject> {
        private final String greatProperty;

        @DataBoundConstructor
        public ExcitingObject(final String greatProperty) {
            this.greatProperty = greatProperty;
        }

        public String getGreatProperty() {
            return greatProperty;
        }

        @Override
        public Descriptor<ExcitingObject> getDescriptor() {
            return Jenkins.get().getDescriptor(ExcitingObject.class);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExcitingObject that = (ExcitingObject) o;
            if (!Objects.equals(greatProperty, that.greatProperty))
                return false;
            return true;
        }

        @Override
        public int hashCode() {
            return greatProperty != null ? greatProperty.hashCode() : 0;
        }

        @Override
        public String toString() {
            return "ExcitingObject[" + greatProperty + ']';
        }

        @Extension
        public static final class ExcitingDescriptor extends Descriptor<ExcitingObject> {
            public ExcitingDescriptor() {
                super(ExcitingObject.class);
            }
        }
    }

    public static final class ExcitingObjectContainer extends AbstractDescribableImpl<ExcitingObjectContainer> {
        String containerName;
        List<ExcitingObject> excitingObjectList;

        @DataBoundConstructor
        public ExcitingObjectContainer(String containerName, List<ExcitingObject> excitingObjectList) {
            this.containerName = containerName;
            this.excitingObjectList = excitingObjectList;
        }

        public String getContainerName() {
            return containerName;
        }

        public List<ExcitingObject> getExcitingObjectList() {
            return excitingObjectList;
        }

        @Extension
        public static final class DescriptorImpl extends Descriptor<ExcitingObjectContainer> {
        }
    }

    @TestExtension
    public static final class RootActionImpl extends InvisibleAction implements Describable<RootActionImpl>, RootAction {

        public ArrayList<ExcitingObject> testRepeatable;
        public ArrayList<ExcitingObject> defaults;
        public List<ExcitingObjectContainer> testRepeatableContainer;

        @Override
        public Descriptor<RootActionImpl> getDescriptor() {
            return Objects.requireNonNull(Jenkins.get().getDescriptorByType(DescriptorImpl.class));
        }

        @TestExtension
        public static final class DescriptorImpl extends Descriptor<RootActionImpl> {}

        @Override
        public String getUrlName() {
            return "self";
        }
    }
}
