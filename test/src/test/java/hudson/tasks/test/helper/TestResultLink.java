package hudson.tasks.test.helper;

import com.gargoylesoftware.htmlunit.html.HtmlAnchor;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

public class TestResultLink extends AbstractTestResultLink<TestResultLink> {

    public static final String TEST_RESULT_STRING = "Test Result";
    public static final String AGGREGATED_TEST_RESULT_STRING = "Aggregated Test Result";

    TestResultLink(HtmlAnchor testResultLink) {
        super(testResultLink);
    }

    public TestResultLink assertHasTestResultText() {
        assertThat(testResultLink.getTextContent(), containsString(TEST_RESULT_STRING));
        return this;
    }

    public TestResultLink assertHasAggregatedTestResultText() {
        assertThat(testResultLink.getTextContent(), containsString(AGGREGATED_TEST_RESULT_STRING));
        return this;
    }

}
