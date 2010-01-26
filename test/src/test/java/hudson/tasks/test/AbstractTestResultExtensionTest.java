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
import hudson.ExtensionList;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TouchBuilder;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * A test case to make sure that the AbstractTestResult extension mechanism
 * is working properly. 
 */
public class AbstractTestResultExtensionTest extends HudsonTestCase {

    public void testFindingTrivialExtension() {

        ExtensionList<AbstractTestResult> all = AbstractTestResult.all();
        assertFalse("we should have at least one AbstractTestResult", all.isEmpty());

        for (AbstractTestResult result : all) {
            System.out.println("found result: " + result);
        }
    }

    public void testTrivialRecorder() throws Exception, TimeoutException, ExecutionException, InterruptedException {
        FreeStyleProject project = createFreeStyleProject("trivialtest");
        TrivialTestResultRecorder recorder = new TrivialTestResultRecorder();
        project.getPublishersList().add(recorder);
        project.getBuildersList().add(new TouchBuilder());

        FreeStyleBuild build = project.scheduleBuild2(0).get(5, TimeUnit.MINUTES); /* leave room for debugging*/
        assertBuildStatus(Result.SUCCESS, build);
        TrivialTestResultAction action = build.getAction(TrivialTestResultAction.class);
        assertNotNull("we should have an action", action);
        assertNotNull("parent action should have an owner", action.owner); 
        Object resultObject = action.getResult();
        assertNotNull("we should have a result");
        assertTrue("result should be an AbstractTestResult",
                resultObject instanceof AbstractTestResult);
        AbstractTestResult abstractResult = (AbstractTestResult) resultObject;
        AbstractBuild<?,?> ownerBuild = abstractResult.getOwner();
        assertNotNull("we should have an owner", ownerBuild);
//      Semantically, we should *not* have a parent!
//        TestObject parent = abstractResult.getParent();
//        assertNotNull("we should have a parent", parent);
        assertNotNull("we should have a list of test actions", abstractResult.getTestActions());
        // TODO: is it okay if the list is empty? 
        

        // TODO: watch out for owner.getPreviousBuild() and owner.getNextBuild();
        // called from some jelly
        String description = abstractResult.getDescription();
        assertNotNull(description); 




        // Validate that there are test results where I expect them to be:
        HudsonTestCase.WebClient wc = new HudsonTestCase.WebClient();
        HtmlPage projectPage = wc.getPage(project);
        assertGoodStatus(projectPage);
        HtmlPage testReportPage = wc.getPage(project, "/lastBuild/testReport/");
        assertGoodStatus(testReportPage);


    }
}


