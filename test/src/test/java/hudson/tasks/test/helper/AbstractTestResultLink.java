package hudson.tasks.test.helper;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import java.net.MalformedURLException;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

public class AbstractTestResultLink<T extends AbstractTestResultLink<T>> {
    protected HtmlAnchor testResultLink;

    public AbstractTestResultLink(HtmlAnchor testResultLink) {
        this.testResultLink = testResultLink;
    }

    public String getResultText() {
        return testResultLink.getNextSibling().asText();
    }
    public T assertNoTests() {
        assertThat(getResultText(), containsString("no tests"));
        return castToConcreteType();
    }

    public T assertHasTests() {
        // Text is either "(no failures)" or "<n> failure(s)"
        assertThat(getResultText(), containsString("failure"));
        return castToConcreteType();
    }

    public TestResultsPage follow() throws Exception {
        return new TestResultsPage((HtmlPage) testResultLink.openLinkInNewWindow());
    }

    @SuppressWarnings("unchecked")
    private T castToConcreteType() {
        return (T) this;
    }

}
