package hudson.tasks.test.helper;

import com.gargoylesoftware.htmlunit.html.HtmlPage;

public class TestResultsPage {
    protected HtmlPage htmlPage;

    public TestResultsPage(HtmlPage htmlPage) {
        this.htmlPage = htmlPage;
    }

    public void hasLinkToTest(String testName) {
        htmlPage.getAnchorByText(testName);
    }

    public void hasLinkToTestResultOfBuild(String projectName, int buildNumber) {
        htmlPage.getAnchorByText(projectName + " #" + buildNumber);
    }
}
