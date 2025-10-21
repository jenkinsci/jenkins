/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.TreeSet;
import jenkins.model.DirectlyModifiableTopLevelItemGroup;
import net.sf.json.JSONObject;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;

@WithJenkins
class ViewDescriptorTest {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    /** Checks that {@link ViewDescriptor#doAutoCompleteCopyNewItemFrom} honors {@link DirectlyModifiableTopLevelItemGroup#canAdd}. */
    @Test
    void canAdd() throws Exception {
        MockFolder d1 = r.createFolder("d1");
        d1.createProject(MockFolder.class, "sub");
        d1.createProject(FreeStyleProject.class, "prj");
        MockFolder d2 = r.jenkins.createProject(RestrictiveFolder.class, "d2");
        assertContains(r.jenkins.getDescriptorByType(AllView.DescriptorImpl.class).doAutoCompleteCopyNewItemFrom("../d1/", d2), "../d1/prj");
    }

    @SuppressWarnings("rawtypes") // the usual API mistakes
    public static class RestrictiveFolder extends MockFolder {

        @SuppressWarnings("checkstyle:redundantmodifier")
        public RestrictiveFolder(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override
        public boolean canAdd(TopLevelItem item) {
            return item instanceof FreeStyleProject;
        }

        @TestExtension("canAdd") public static class DescriptorImpl extends TopLevelItemDescriptor {

            @Override public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new RestrictiveFolder(parent, name);
            }

        }

    }

    private void assertContains(AutoCompletionCandidates c, String... values) {
        assertEquals(new TreeSet<>(Arrays.asList(values)), new TreeSet<>(c.getValues()));
    }

    @Test
    @Issue("JENKINS-60579")
    void invisiblePropertiesOnViewShoudBePersisted() throws Exception {

        //GIVEN a listView that have an invisible property
        ListView myListView = new ListView("Rock");
        myListView.setRecurse(true);
        myListView.setIncludeRegex(".*");

        CustomInvisibleProperty invisibleProperty = new CustomInvisibleProperty();
        invisibleProperty.setSomeProperty("You cannot see me.");
        invisibleProperty.setView(myListView);
        myListView.getProperties().add(invisibleProperty);

        r.jenkins.addView(myListView);

        assertEquals(
                "You cannot see me.",
                r.jenkins
                        .getView("Rock")
                        .getProperties()
                        .get(CustomInvisibleProperty.class)
                        .getSomeProperty());

        //WHEN the users goes with "Edit View" on the configure page
        JenkinsRule.WebClient client = r.createWebClient();
        HtmlPage editViewPage = client.getPage(myListView, "configure");

        //THEN the invisible property is not displayed on page
        assertFalse(editViewPage.asNormalizedText().contains("CustomInvisibleProperty"),
                    "CustomInvisibleProperty should not be displayed on the View edition page UI.");


        HtmlForm editViewForm = editViewPage.getFormByName("viewConfig");
        editViewForm.getTextAreaByName("_.description").setText("This list view is awesome !");
        r.submit(editViewForm);

        //Check that the description is updated on view
        assertThat(client.getPage(myListView).asNormalizedText(), containsString("This list view is awesome !"));

        //AND THEN after View save, the invisible property is still persisted with the View.
        assertNotNull(r.jenkins.getView("Rock").getProperties().get(CustomInvisibleProperty.class),
                      "The CustomInvisibleProperty should be persisted on the View.");
        assertEquals(
                "You cannot see me.",
                r.jenkins
                        .getView("Rock")
                        .getProperties()
                        .get(CustomInvisibleProperty.class)
                        .getSomeProperty());

    }

    private static class CustomInvisibleProperty extends ViewProperty {

        private String someProperty;

        public void setSomeProperty(String someProperty) {
            this.someProperty = someProperty;
        }

        public String getSomeProperty() {
            return this.someProperty;
        }

        CustomInvisibleProperty() {
            this.someProperty = "undefined";
        }

        @Override
        public ViewProperty reconfigure(StaplerRequest2 req, JSONObject form) {
            return this;
        }

        @TestExtension
        public static final class CustomInvisibleDescriptorImpl extends ViewPropertyDescriptor {

            @Override
            public String getId() {
                return "CustomInvisibleDescriptorImpl";
            }

            @Override
            public boolean isEnabledFor(View view) {
                return true;
            }
        }

        @TestExtension
        public static final class CustomInvisibleDescriptorVisibilityFilterImpl extends DescriptorVisibilityFilter {

            @Override
            public boolean filter(Object context, Descriptor descriptor) {
                return false;
            }
        }
    }

}
