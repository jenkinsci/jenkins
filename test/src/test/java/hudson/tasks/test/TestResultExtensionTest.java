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
package hudson.tasks.test;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TouchBuilder;

import java.util.concurrent.TimeUnit;


/**
 * A test case to make sure that the TestResult extension mechanism
 * is working properly. 
 */
public class TestResultExtensionTest extends HudsonTestCase {

    public void testTrivialRecorder() throws Exception {
        FreeStyleProject project = createFreeStyleProject("trivialtest");
        TrivialTestResultRecorder recorder = new TrivialTestResultRecorder();
        project.getPublishersList().add(recorder);
        project.getBuildersList().add(new TouchBuilder());

        FreeStyleBuild build = project.scheduleBuild2(0).get(5, TimeUnit.MINUTES); /* leave room for debugging*/
        assertBuildStatus(Result.SUCCESS, build);
        TrivialTestResultAction action = build.getAction(TrivialTestResultAction.class);
        assertNotNull("we should have an action", action);
        assertNotNull("parent action should have an owner", action.run);
        Object resultObject = action.getResult();
        assertNotNull("we should have a result");
        assertTrue("result should be an TestResult",
                resultObject instanceof TestResult);
        TestResult result = (TestResult) resultObject;
        Run<?,?> ownerBuild = result.getRun();
        assertNotNull("we should have an owner", ownerBuild);
        assertNotNull("we should have a list of test actions", result.getTestActions());

        // Validate that there are test results where I expect them to be:
        HudsonTestCase.WebClient wc = new HudsonTestCase.WebClient();
        HtmlPage projectPage = wc.getPage(project);
        assertGoodStatus(projectPage);
        HtmlPage testReportPage = wc.getPage(project, "/lastBuild/testReport/");
        assertGoodStatus(testReportPage);


    }
}


