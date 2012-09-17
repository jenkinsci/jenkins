/*
 * The MIT License
 *
 * Copyright (c) 2009, Yahoo!, Inc.
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
package hudson.tasks.junit;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Project;
import hudson.model.Result;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.List;

public class HistoryTest extends HudsonTestCase {
     private FreeStyleProject project;

    private static final String PROJECT_NAME = "wonky";


    @Override
    protected void setUp() throws Exception {
        super.setUp();

        List<Project> projects = this.jenkins.getProjects();
        Project theProject = null;
        for (Project p : projects) {
            if (p.getName().equals(PROJECT_NAME)) theProject = p;
        }
        assertNotNull("We should have a project named " + PROJECT_NAME, theProject);

        assertTrue( theProject instanceof FreeStyleProject);
        project = (FreeStyleProject) theProject;
    }


    @LocalData
    public void testFailedSince() throws Exception {
        assertNotNull("project should exist", project);

        // Check the status of a few builds
        FreeStyleBuild build4 = project.getBuildByNumber(4);
        assertNotNull("build4", build4);
        assertBuildStatus(Result.FAILURE, build4);

        FreeStyleBuild build7 = project.getBuildByNumber(7);
        assertNotNull("build7", build7);
        assertBuildStatus(Result.SUCCESS, build7);

        TestResult tr = build4.getAction(TestResultAction.class).getResult();
        assertEquals(2,tr.getFailedTests().size());

        // In build 4, we expect these tests to have failed since these builds
        // org.jvnet.hudson.examples.small.deep.DeepTest.testScubaGear failed since 3
        // org.jvnet.hudson.examples.small.MiscTest.testEleanor failed since 3

        PackageResult deepPackage = tr.byPackage("org.jvnet.hudson.examples.small.deep");
        assertNotNull("deepPackage", deepPackage);
        assertTrue("package is failed", !deepPackage.isPassed());
        ClassResult deepClass = deepPackage.getClassResult("DeepTest");
        assertNotNull(deepClass);
        assertTrue("class is failed", !deepClass.isPassed());
        CaseResult scubaCase = deepClass.getCaseResult("testScubaGear");
        assertNotNull(scubaCase);
        assertTrue("scubaCase case is failed", !scubaCase.isPassed());
        int scubaFailedSince = scubaCase.getFailedSince();
        assertEquals("scubaCase should have failed since build 3", 3, scubaFailedSince);


        // In build 5 the scuba test begins to pass
        TestResult tr5 = project.getBuildByNumber(5).getAction(TestResultAction.class).getResult();
        assertEquals(1,tr5.getFailedTests().size());
        deepPackage = tr5.byPackage("org.jvnet.hudson.examples.small.deep");
        assertNotNull("deepPackage", deepPackage);
        assertTrue("package is passed", deepPackage.isPassed());
        deepClass = deepPackage.getClassResult("DeepTest");
        assertNotNull(deepClass);
        assertTrue("class is passed", deepClass.isPassed());
        scubaCase = deepClass.getCaseResult("testScubaGear");
        assertNotNull(scubaCase);
        assertTrue("scubaCase case is passed", scubaCase.isPassed());

        // In build5, testEleanor has been failing since build 3
        PackageResult smallPackage = tr5.byPackage("org.jvnet.hudson.examples.small");
        ClassResult miscClass = smallPackage.getClassResult("MiscTest");
        CaseResult eleanorCase = miscClass.getCaseResult("testEleanor");
        assertTrue("eleanor failed", !eleanorCase.isPassed());
        assertEquals("eleanor has failed since build 3", 3, eleanorCase.getFailedSince()); 
    }
}
