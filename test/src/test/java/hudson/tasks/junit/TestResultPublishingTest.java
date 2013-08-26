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

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.*;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Project;
import hudson.model.Result;
import hudson.model.Run;
import hudson.slaves.DumbSlave;
import hudson.tasks.Builder;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TouchBuilder;
import org.jvnet.hudson.test.recipes.LocalData;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TestResultPublishingTest extends HudsonTestCase {
    private FreeStyleProject project;
    private JUnitResultArchiver archiver;
    private final String BASIC_TEST_PROJECT = "percival";
    private final String TEST_PROJECT_WITH_HISTORY = "wonky";


    @Override
    protected void setUp() throws Exception {
        super.setUp();
        project = createFreeStyleProject(BASIC_TEST_PROJECT);
        archiver = new JUnitResultArchiver("*.xml");
        project.getPublishersList().add(archiver);
        project.getBuildersList().add(new TouchBuilder());
    }

    @LocalData
    public void testBasic() throws Exception {
        FreeStyleBuild build = project.scheduleBuild2(0).get(30, TimeUnit.SECONDS);

        assertTestResults(build);

        HudsonTestCase.WebClient wc = new HudsonTestCase.WebClient();
        wc.getPage(project); // project page
        wc.getPage(build); // build page
        wc.getPage(build, "testReport");  // test report
        wc.getPage(build, "testReport/hudson.security"); // package
        wc.getPage(build, "testReport/hudson.security/HudsonPrivateSecurityRealmTest/"); // class
        wc.getPage(build, "testReport/hudson.security/HudsonPrivateSecurityRealmTest/testDataCompatibilityWith1_282/"); // method
    }


    @LocalData
    public void testSlave() throws Exception {
        DumbSlave s = createOnlineSlave();
        project.setAssignedLabel(s.getSelfLabel());

        FilePath src = new FilePath(jenkins.getRootPath(), "jobs/" + BASIC_TEST_PROJECT + "/workspace/");
        assertNotNull(src);
        FilePath dest = s.getWorkspaceFor(project);
        assertNotNull(dest);
        src.copyRecursiveTo("*.xml", dest);

        testBasic();
    }
  

    /**
     * Verify that we can successfully parse and display test results in the
     * open junit test result publishing toolchain. Ensure that we meet this
     * compatibility requirement:
     * From users' point of view, Hudson core JUnit should continue
     * to work as if nothing has changed
     * - Old testReport URLs should still work
     */
    @LocalData
    public void testOpenJUnitPublishing() throws IOException, SAXException {
        List<Project> projects = this.jenkins.getProjects();
        // Make sure there's a project named TEST_PROJECT_WITH_HISTORY
        Project proj = null;
        for (Project p : projects) {
            if (p.getName().equals(TEST_PROJECT_WITH_HISTORY)) proj = p;
        }
        assertNotNull("We should have a project named " + TEST_PROJECT_WITH_HISTORY, proj);

        // Validate that there are test results where I expect them to be:
        HudsonTestCase.WebClient wc = new HudsonTestCase.WebClient();

        // On the project page:
        HtmlPage projectPage = wc.getPage(proj);
        //      we should have a link that reads "Latest Test Result"
        //      that link should go to http://localhost:8080/job/breakable/lastBuild/testReport/
        assertXPath(projectPage, "//a[@href='lastCompletedBuild/testReport/']");
        assertXPathValue(projectPage, "//a[@href='lastCompletedBuild/testReport/']", "Latest Test Result");
        assertXPathValueContains(projectPage, "//a[@href='lastCompletedBuild/testReport/']", "Latest Test Result");
        //      after "Latest Test Result" it should say "no failures"
        assertXPathResultsContainText(projectPage, "//td", "(no failures)");
        //      there should be a test result trend graph
        assertXPath(projectPage, "//img[@src='test/trend']");
        // the trend graph should be served up with a good http status
        Page trendGraphPage = wc.goTo(proj.getUrl() + "/test/trend", "image/png");
        assertGoodStatus(trendGraphPage);

        // The trend graph should be clickable and take us to a run details page
        Object imageNode = projectPage.getFirstByXPath("//img[@src='test/trend']");
        assertNotNull("couldn't find any matching nodes", imageNode);
        assertTrue("image node should be an HtmlImage object", imageNode instanceof HtmlImage);
        // TODO: Check that we can click on the graph and get to a particular run. How do I do this with HtmlUnit?

        XmlPage xmlProjectPage = wc.goToXml(proj.getUrl() + "/lastBuild/testReport/api/xml");
        assertXPath(xmlProjectPage, "/testResult");
        assertXPath(xmlProjectPage, "/testResult/suite");
        assertXPath(xmlProjectPage, "/testResult/failCount");
        assertXPathValue(xmlProjectPage, "/testResult/failCount", "0");
        assertXPathValue(xmlProjectPage, "/testResult/passCount", "4");
        assertXPathValue(xmlProjectPage, "/testResult/skipCount", "0");
        String[] packages = {"org.jvnet.hudson.examples.small.AppTest", "org.jvnet.hudson.examples.small.MiscTest", "org.jvnet.hudson.examples.small.deep.DeepTest"};
        for (String packageName : packages) {
            assertXPath(xmlProjectPage, "/testResult/suite/case/className[text()='" + packageName + "']");
        }

        // Go to a page that we know has a failure
        HtmlPage buildPage = wc.getPage(proj.getBuildByNumber(3));
        assertGoodStatus(buildPage);
        // We expect to see one failure, for com.yahoo.breakable.misc.UglyTest.becomeUglier
        // which should link to http://localhost:8080/job/wonky/3/testReport/org.jvnet.hudson.examples.small/MiscTest/testEleanor/
        assertXPathResultsContainText(buildPage, "//a", "org.jvnet.hudson.examples.small.MiscTest.testEleanor");
        HtmlAnchor failingTestLink = buildPage.getFirstAnchorByText("org.jvnet.hudson.examples.small.MiscTest.testEleanor");
        assertNotNull(failingTestLink);
        Page failingTestPage = failingTestLink.click();
        assertGoodStatus(failingTestPage);

        // Go to the xml page for a build we know has failures
        XmlPage xmlBuildPage = wc.goToXml(proj.getBuildByNumber(3).getUrl() + "/api/xml");
        assertXPathValue(xmlBuildPage, "//failCount", "2");
        assertXPathValue(xmlBuildPage, "//skipCount", "0");
        assertXPathValue(xmlBuildPage, "//totalCount", "4");
        assertXPathValue(xmlBuildPage, "//result", "FAILURE");

        // Check overall test result counts
        XmlPage xmlTestReportPage = wc.goToXml(proj.getBuildByNumber(3).getUrl() + "/testReport/api/xml");
        assertXPathValue(xmlTestReportPage, "/testResult/failCount", "2");
        assertXPathValue(xmlTestReportPage, "/testResult/passCount", "2");
        assertXPathValue(xmlTestReportPage, "/testResult/skipCount", "0");

        // Make sure the right tests passed and failed
        assertXPathValue(xmlTestReportPage, "/testResult/suite/case[className/text()='org.jvnet.hudson.examples.small.AppTest']/status", "PASSED");
        assertXPathValue(xmlTestReportPage, "/testResult/suite/case[name/text()='testEleanor']/status", "FAILED");


        // TODO: implement more of these tests
        // On the lastBuild/testReport page:
        //      Breadcrumbs should read #6 > Test Result  where Test Result is a link to this page
        //      inside of div id="main-panel" we should find the text "0 failures (-1)"
        //      we should have a blue bar which is blue all the way across: div style="width: 100%; height: 1em; background-color: rgb(114, 159, 207);
        //      we should find the words "7 tests (?0)"
        //      we should find the words "All Tests"
        //      we should find a table

        //      Inside that table, there should be the following rows:
        //           org.jvnet.hudson.examples.small 	0ms   0 -1 0   3
        //          org.jvnet.hudson.examples.small.deep 	4ms 0 0 0  1
        Run theRun = proj.getBuildByNumber(7);
        assertTestResultsAsExpected(wc, theRun, "/testReport",
                "org.jvnet.hudson.examples.small", "0 ms", "SUCCESS",
                /* total tests expected, diff */ 3, 0,
                /* fail count expected, diff */ 0, -1,
                /* skip count expected, diff */ 0, 0);

        assertTestResultsAsExpected(wc, theRun, "/testReport",
                "org.jvnet.hudson.examples.small.deep", "4 ms", "SUCCESS",
                /* total tests expected, diff */ 1, 0,
                /* fail count expected, diff */ 0, 0,
                /* skip count expected, diff */ 0, 0);

        // TODO: more, more, more.
        // TODO: test report history by package

    }

    /**
     * Test to demonstrate bug HUDSON-5246, inter-build diffs for junit test results are wrong
     */
    @Bug(5246)
    @LocalData
    public void testInterBuildDiffs() throws IOException, SAXException {
        List<Project> projects = this.jenkins.getProjects();
        // Make sure there's a project named TEST_PROJECT_WITH_HISTORY
        Project proj = null;
        for (Project p : projects) {
            if (p.getName().equals(TEST_PROJECT_WITH_HISTORY)) proj = p;
        }
        assertNotNull("We should have a project named " + TEST_PROJECT_WITH_HISTORY, proj);

        // Validate that there are test results where I expect them to be:
        HudsonTestCase.WebClient wc = new HudsonTestCase.WebClient();
        Run theRun = proj.getBuildByNumber(4);
        assertTestResultsAsExpected(wc, theRun, "/testReport",
                        "org.jvnet.hudson.examples.small", "12 ms", "FAILURE",
                        /* total tests expected, diff */ 3, 0,
                        /* fail count expected, diff */ 1, 0,
                        /* skip count expected, diff */ 0, 0);


    }

    /**
     * Make sure the open junit publisher shows junit history
     * @throws IOException
     * @throws SAXException
     */
    @LocalData
    public void testHistoryPageOpenJunit() throws IOException, SAXException {
        List<Project> projects = this.jenkins.getProjects();
        // Make sure there's a project named breakable
        Project proj = null;
        for (Project p : projects) {
            if (p.getName().equals(TEST_PROJECT_WITH_HISTORY)) {
                proj = p;
                break;
            }
        }
        assertNotNull("We should have a project named " + TEST_PROJECT_WITH_HISTORY, proj);

        // Validate that there are test results where I expect them to be:
        HudsonTestCase.WebClient wc = new HudsonTestCase.WebClient();

        HtmlPage historyPage = wc.getPage(proj.getBuildByNumber(7),"/testReport/history/");
        assertGoodStatus(historyPage);
        assertXPath(historyPage, "//img[@id='graph']");
        assertXPath(historyPage, "//table[@id='testresult']");
        HtmlElement wholeTable = historyPage.getElementById("testresult");
        assertNotNull("table with id 'testresult' exists", wholeTable);
        assertTrue("wholeTable is a table", wholeTable instanceof HtmlTable);
        HtmlTable table = (HtmlTable) wholeTable;

        // We really want to call table.getRowCount(), but
        // it returns 1, not the real answer,
        // because this table has *two* tbody elements,
        // and getRowCount() only seems to count the *first* tbody.
        // Maybe HtmlUnit can't handle the two tbody's. In any case,
        // the tableText.contains tests do a (ahem) passable job
        // of detecting whether the history results are present.

        String tableText = table.getTextContent();
        assertTrue("Table text is missing the project name",
                tableText.contains(TEST_PROJECT_WITH_HISTORY));
        assertTrue("Table text is missing the build number",
                tableText.contains("7"));
        assertTrue("Table text is missing the test duration",
                tableText.contains("4 ms"));
    }

    @Bug(19186)
    public void testBrokenResultFile() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder());
        p.getPublishersList().add(new JUnitResultArchiver("TEST-foo.xml", false, null));
        assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0).get());
    }
    private static final class TestBuilder extends Builder {
        @Override public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            build.getWorkspace().child("TEST-foo.xml").write("<bogus>", null);
            return true;
        }
    }
  
    void assertStringEmptyOrNull(String msg, String str) {
        if (str==null)
            return;
        if (str.equals(""))
            return;
        fail(msg + "(should be empty or null) : \'" + str + "\'"); 
    }

    void assertPaneDiffText(String msg, int expectedValue, Object paneObj) { 
        assertTrue( "paneObj should be an HtmlElement, it was " + paneObj.getClass(), paneObj instanceof HtmlElement );
        String paneText = ((HtmlElement) paneObj).asText();
        if (expectedValue==0) {
            assertStringEmptyOrNull(msg, paneText);
        } else {
            String expectedString =
             (expectedValue >= 1 ? "+" : "-")
                    + Math.abs(expectedValue);
            assertEquals(msg, expectedString, paneText);
        }
    }

    void assertTestResultsAsExpected(WebClient wc, Run run, String restOfUrl,
                                     String packageName,
                                     String expectedResult, String expectedDurationStr,
                                     int expectedTotalTests, int expectedTotalDiff,
                                     int expectedFailCount, int expectedFailDiff,
                                     int expectedSkipCount, int expectedSkipDiff) throws IOException, SAXException {

        // TODO: verify expectedResult
        // TODO: verify expectedDuration

        XmlPage xmlPage = wc.goToXml(run.getUrl() + restOfUrl + "/" + packageName + "/api/xml");
        int expectedPassCount = expectedTotalTests - expectedFailCount - expectedSkipCount;
        // Verify xml results
        assertXPathValue(xmlPage, "/packageResult/failCount", Integer.toString(expectedFailCount));
        assertXPathValue(xmlPage, "/packageResult/skipCount", Integer.toString(expectedSkipCount));
        assertXPathValue(xmlPage, "/packageResult/passCount", Integer.toString(expectedPassCount));
        assertXPathValue(xmlPage, "/packageResult/name", packageName);

        // TODO: verify html results
        HtmlPage testResultPage =   wc.getPage(run, restOfUrl);

        // Verify inter-build diffs in html table
        String xpathToFailDiff =  "//table[@id='testresult']//tr[td//span[text()=\"" + packageName + "\"]]/td[4]";
        String xpathToSkipDiff =  "//table[@id='testresult']//tr[td//span[text()=\"" + packageName + "\"]]/td[6]";
        String xpathToTotalDiff = "//table[@id='testresult']//tr[td//span[text()=\"" + packageName + "\"]]/td[last()]";

        Object totalDiffObj = testResultPage.getFirstByXPath(xpathToTotalDiff);
        assertPaneDiffText("total diff", expectedTotalDiff, totalDiffObj);

        Object failDiffObj = testResultPage.getFirstByXPath(xpathToFailDiff);
        assertPaneDiffText("failure diff", expectedFailDiff, failDiffObj);

        Object skipDiffObj = testResultPage.getFirstByXPath(xpathToSkipDiff);
        assertPaneDiffText("skip diff", expectedSkipDiff, skipDiffObj);

        // TODO: The link in the table for each of the three packages in the testReport table should link to a by-package page,
        // TODO: for example, http://localhost:8080/job/breakable/lastBuild/testReport/com.yahoo.breakable.misc/

    }

    // TODO: Make sure that we meet this compatibility requirement:
    // TODO: From users' point of view, Open Source  *Unit publishers should continue to work as if nothing has changed
    // TODO: * Old testReport URLs should still work


    private void assertTestResults(FreeStyleBuild build) {
        TestResultAction testResultAction = build.getAction(TestResultAction.class);
        assertNotNull("no TestResultAction", testResultAction);

        TestResult result = testResultAction.getResult();
        assertNotNull("no TestResult", result);

        assertEquals("should have 1 failing test", 1, testResultAction.getFailCount());
        assertEquals("should have 1 failing test", 1, result.getFailCount());

        assertEquals("should have 132 total tests", 132, testResultAction.getTotalCount());
        assertEquals("should have 132 total tests", 132, result.getTotalCount());
    }

}
