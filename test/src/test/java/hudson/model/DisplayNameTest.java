/*
 *  The MIT License
 *
 *  Copyright 2011 Yahoo!, Inc.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author kingfai
 *
 */
@WithJenkins
class DisplayNameTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testRenameJobWithNoDisplayName() throws Exception {
        final String projectName = "projectName";
        final String newProjectName = "newProjectName";
        FreeStyleProject project = j.createFreeStyleProject(projectName);
        assertEquals(projectName, project.getDisplayName());

        project.renameTo(newProjectName);
        assertEquals(newProjectName, project.getDisplayName());
    }

    @Test
    void testRenameJobWithDisplayName() throws Exception {
        final String projectName = "projectName";
        final String newProjectName = "newProjectName";
        final String displayName = "displayName";
        FreeStyleProject project = j.createFreeStyleProject(projectName);
        project.setDisplayName(displayName);
        assertEquals(displayName, project.getDisplayName());

        project.renameTo(newProjectName);
        assertEquals(displayName, project.getDisplayName());
    }

    @SuppressWarnings("rawtypes")
    @Test
    void testCopyJobWithNoDisplayName() throws Exception {
        final String projectName = "projectName";
        final String newProjectName = "newProjectName";
        FreeStyleProject project = j.createFreeStyleProject(projectName);
        assertEquals(projectName, project.getDisplayName());

        AbstractProject newProject = Jenkins.get().copy((AbstractProject) project, newProjectName);
        assertEquals(newProjectName, newProject.getName());
        assertEquals(newProjectName, newProject.getDisplayName());
    }

    @SuppressWarnings("rawtypes")
    @Test
    void testCopyJobWithDisplayName() throws Exception {
        final String projectName = "projectName";
        final String newProjectName = "newProjectName";
        final String oldDisplayName = "oldDisplayName";
        FreeStyleProject project = j.createFreeStyleProject(projectName);
        project.setDisplayName(oldDisplayName);
        assertEquals(oldDisplayName, project.getDisplayName());

        AbstractProject newProject = Jenkins.get().copy((AbstractProject) project, newProjectName);
        assertEquals(newProjectName, newProject.getName());
        assertEquals(newProjectName, newProject.getDisplayName());

    }

    @Issue("JENKINS-18074")
    @Test
    void copyJobWithDisplayNameToDifferentFolder() throws Exception {
        MockFolder d1 = j.createFolder("d1");
        FreeStyleProject job = d1.createProject(FreeStyleProject.class, "job");
        job.setDisplayName("My Job");
        MockFolder d2 = j.jenkins.copy(d1, "d2");
        FreeStyleProject j2 = (FreeStyleProject) d2.getItem("job");
        assertNotNull(j2);
        assertEquals("My Job", j2.getDisplayName());
    }

}
