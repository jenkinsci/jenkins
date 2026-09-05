/*
 * The MIT License
 *
 * Copyright (c) 2026, Ahmed Fatthi
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

package hudson.scm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.Descriptor.FormException;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import java.util.List;
import net.sf.json.JSONObject;
import org.htmlunit.html.HtmlForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

@WithJenkins
public class SCMSTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("JENKINS-75352")
    @SuppressWarnings("deprecation")
    void configurationSkipsInapplicableSCM() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        SCMDescriptor<?> hidden = j.jenkins.getDescriptorByType(InapplicableSCM.DescriptorImpl.class);
        SCMDescriptor<?> selected = j.jenkins.getDescriptorByType(ConfiguredSCM.DescriptorImpl.class);
        List<SCMDescriptor<?>> applicable = SCM._for((Job) project);
        assertFalse(applicable.contains(hidden));
        assertTrue(SCM.all().indexOf(hidden) < SCM.all().indexOf(selected));
        int selectedIndex = applicable.indexOf(selected);
        assertTrue(selectedIndex >= 0);
        assertTrue(selectedIndex < SCM.all().indexOf(selected));

        HtmlForm form = j.createWebClient().getPage(project, "configure").getFormByName("config");
        form.getRadioButtonsByName("scm").get(selectedIndex).click();
        form.getInputByName("_.repository").setValue("https://example.com/repository");
        int generation = selected.getGeneration();
        j.submit(form);

        ConfiguredSCM scm = assertInstanceOf(ConfiguredSCM.class, project.getScm());
        assertEquals("https://example.com/repository", scm.getRepository());
        assertEquals(generation + 1, selected.getGeneration());
        project.doReload();
        assertEquals("https://example.com/repository", assertInstanceOf(ConfiguredSCM.class, project.getScm()).getRepository());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MAX_VALUE})
    void rejectsInvalidIndex(int index) throws Exception {
        StaplerRequest2 request = mock(StaplerRequest2.class);
        JSONObject config = new JSONObject();
        config.put("value", index);
        JSONObject form = new JSONObject();
        form.put("scm", config);
        when(request.getSubmittedForm()).thenReturn(form);

        assertThrows(FormException.class, () -> SCMS.parseSCM(request, j.createFreeStyleProject()));
    }

    @Test
    @SuppressWarnings("deprecation")
    void absentSCMUsesNullSCM() throws Exception {
        StaplerRequest2 request = mock(StaplerRequest2.class);
        JSONObject form = new JSONObject();
        form.put("scm", JSONObject.fromObject("null"));
        when(request.getSubmittedForm()).thenReturn(form);
        SCMDescriptor<?> descriptor = j.jenkins.getDescriptorByType(NullSCM.DescriptorImpl.class);
        int generation = descriptor.getGeneration();

        assertInstanceOf(NullSCM.class, SCMS.parseSCM(request, j.createFreeStyleProject()));
        assertEquals(generation + 1, descriptor.getGeneration());
    }

    public static class InapplicableSCM extends NullSCM {
        @TestExtension("configurationSkipsInapplicableSCM")
        public static class DescriptorImpl extends SCMDescriptor<InapplicableSCM> {
            public DescriptorImpl() {
                super(null);
            }

            @Override
            public boolean isApplicable(Job project) {
                return !(project instanceof FreeStyleProject);
            }

            @Override
            public String getDisplayName() {
                return "A Inapplicable SCM";
            }
        }
    }

    public static class ConfiguredSCM extends NullSCM {
        private final String repository;

        @DataBoundConstructor
        public ConfiguredSCM(String repository) {
            this.repository = repository;
        }

        public String getRepository() {
            return repository;
        }

        @TestExtension("configurationSkipsInapplicableSCM")
        public static class DescriptorImpl extends SCMDescriptor<ConfiguredSCM> {
            public DescriptorImpl() {
                super(null);
            }

            @Override
            public String getDisplayName() {
                return "Z Configured SCM";
            }
        }
    }
}
