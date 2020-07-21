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

import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Predicate;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Issue;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RepeatablePropertyTest extends HudsonTestCase implements Describable<RepeatablePropertyTest> {

    private static final String VIEW_WITHOUT_DEFAULT = "noDefault";
    private static final String VIEW_WITH_DEFAULT = "withDefault";
    
    public ArrayList<ExcitingObject> testRepeatable;
    public ArrayList<ExcitingObject> defaults;
    public List<ExcitingObjectContainer> testRepeatableContainer;
    
    public void testSimple() throws Exception {
        testRepeatable = createRepeatable();
        assertFormContents(VIEW_WITHOUT_DEFAULT, testRepeatable);
    }
    
    public void testNullFieldNoDefault() throws Exception {
        assertFormContents(VIEW_WITHOUT_DEFAULT, new ArrayList<ExcitingObject>());
    }
    
    public void testNullFieldWithDefault() throws Exception {
        defaults = createRepeatable();
        assertFormContents(VIEW_WITH_DEFAULT, defaults);
    }
    
    public void testFieldNotNullWithDefaultIgnoresDefaults() throws Exception {
        testRepeatable = createRepeatable();
        defaults = new ArrayList<ExcitingObject>(Arrays.asList(
           new ExcitingObject("This default should be ignored"),
           new ExcitingObject("Ignore me too")
        ));
        assertFormContents(VIEW_WITH_DEFAULT, testRepeatable);
    }

    @Issue("JENKINS-37599")
    public void testNestedRepeatableProperty() throws Exception {
        testRepeatableContainer = Collections.emptyList();
        // minimum="1" is set for the upper one,
        // the form should be:
        // * 1 ExcitingObjectCotainer
        // * no ExcitingObject
        final HtmlForm form = getForm("nested");
        List<HtmlTextInput> containerNameInputs = form.getElementsByAttribute("input", "type", "text");
        CollectionUtils.filter(containerNameInputs, new Predicate<HtmlTextInput>() {
            @Override
            public boolean evaluate(HtmlTextInput input) {
                return input.getNameAttribute().endsWith(".containerName");
            }
        });
        List<HtmlTextInput> greatPropertyInputs = form.getElementsByAttribute("input", "type", "text");
        CollectionUtils.filter(greatPropertyInputs, new Predicate<HtmlTextInput>() {
            @Override
            public boolean evaluate(HtmlTextInput input) {
                return input.getNameAttribute().endsWith(".greatProperty");
            }
        });
        assertEquals(1, containerNameInputs.size());
        assertEquals(0, greatPropertyInputs.size());
    }
        
    private void assertFormContents(final String viewName, final ArrayList<ExcitingObject> expected) throws Exception {
        final HtmlForm form = getForm(viewName);
        final List<HtmlTextInput> inputs = toTextInputList(form.getElementsByAttribute("input", "type", "text"));
        assertEquals("size", expected.size(), inputs.size());
        for (int i = 0; i < expected.size(); i++)
            assertEquals(expected.get(i).greatProperty, inputs.get(i).getValueAttribute());
    }
    
    private List<HtmlTextInput> toTextInputList(final List<HtmlElement> inputs) {
        assertNotNull(inputs);
        final List<HtmlTextInput> textInputList = new ArrayList<HtmlTextInput>();
        for (HtmlElement input : inputs) {
            assertTrue(input instanceof HtmlTextInput);
            textInputList.add((HtmlTextInput) input);
        }
        return textInputList;
    }
    
    private ArrayList<ExcitingObject> createRepeatable() {
        return new ArrayList<ExcitingObject>(Arrays.asList(
           new ExcitingObject("A nice thing"),
           new ExcitingObject("I'm even better"),
           new ExcitingObject("Don't bother, I'm not exciting at all")
        ));
    }

    private HtmlForm getForm(final String viewName) throws Exception {
        final HtmlPage page = createWebClient().goTo("self/" + viewName);
        final HtmlForm form = page.getFormByName("config");
        return form;
    }

    public DescriptorImpl getDescriptor() {
        return jenkins.getDescriptorByType(DescriptorImpl.class);
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<RepeatablePropertyTest> {}
        
    public static final class ExcitingObject implements Describable<ExcitingObject> {
        private final String greatProperty;
        @DataBoundConstructor
        public ExcitingObject(final String greatProperty) {
            this.greatProperty = greatProperty;
        }
        public String getGreatProperty() {
            return greatProperty;
        }
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
}
