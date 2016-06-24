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

import java.util.Arrays;
import java.util.TreeSet;
import jenkins.model.DirectlyModifiableTopLevelItemGroup;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.TestExtension;

public class ViewDescriptorTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    /** Checks that {@link ViewDescriptor#doAutoCompleteCopyNewItemFrom} honors {@link DirectlyModifiableTopLevelItemGroup#canAdd}. */
    @Test
    public void canAdd() throws Exception {
        MockFolder d1 = r.createFolder("d1");
        d1.createProject(MockFolder.class, "sub");
        d1.createProject(FreeStyleProject.class, "prj");
        MockFolder d2 = r.jenkins.createProject(RestrictiveFolder.class, "d2");
        assertContains(r.jenkins.getDescriptorByType(AllView.DescriptorImpl.class).doAutoCompleteCopyNewItemFrom("../d1/", d2), "../d1/prj");
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // the usual API mistakes
    public static class RestrictiveFolder extends MockFolder {

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

}
