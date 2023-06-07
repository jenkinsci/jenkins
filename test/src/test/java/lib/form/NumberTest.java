package lib.form;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.junit.Assert.assertEquals;

import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import java.io.IOException;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.javascript.host.event.Event;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.xml.sax.SAXException;

/**
 * Tests for lib/number.jelly.
 */
public class NumberTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void minValidation() throws IOException, SAXException {

        HtmlPage page = j.createWebClient().goTo("minValidation");
        HtmlForm form = page.getFormByName("number");

        String errorMessage;
        HtmlInput input;

        // <input type="number" min="5">
        input = form.getInputByName("min-5");

        errorMessage = typeValueAndGetErrorMessage(input, "2");
        assertEquals(errorMessage, "This value should be larger than 5");

        errorMessage = typeValueAndGetErrorMessage(input, "5");
        assertThat(errorMessage, emptyString());

        errorMessage = typeValueAndGetErrorMessage(input, "17");
        assertThat(errorMessage, emptyString());


        // <input type="number" min="wow">
        input = form.getInputByName("min-wow");

        errorMessage = typeValueAndGetErrorMessage(input, "13");
        assertThat(errorMessage, emptyString());

        errorMessage = typeValueAndGetErrorMessage(input, "89");
        assertThat(errorMessage, emptyString());
    }

    @Test
    public void maxValidation() throws IOException, SAXException {
        HtmlPage page = j.createWebClient().goTo("maxValidation");
        HtmlForm form = page.getFormByName("number");

        String errorMessage;
        HtmlInput input;

        // <input type="number" max="70">
        input = form.getInputByName("max-70");

        errorMessage = typeValueAndGetErrorMessage(input, "58");
        assertThat(errorMessage, emptyString());

        errorMessage = typeValueAndGetErrorMessage(input, "70");
        assertThat(errorMessage, emptyString());

        errorMessage = typeValueAndGetErrorMessage(input, "87");
        assertEquals(errorMessage, "This value should be less than 70");


        // <input type="number" max="wow">
        input = form.getInputByName("max-wow");

        errorMessage = typeValueAndGetErrorMessage(input, "72");
        assertThat(errorMessage, emptyString());

        errorMessage = typeValueAndGetErrorMessage(input, "135");
        assertThat(errorMessage, emptyString());
    }


    @Test
    public void minAndMaxValidation() throws IOException, SAXException {
        HtmlPage page = j.createWebClient().goTo("minAndMaxValidation");
        HtmlForm form = page.getFormByName("number");

        String errorMessage;
        HtmlInput input;

        // <input type="number" min="5" max="70">
        input = form.getInputByName("min-5-max-70");

        errorMessage = typeValueAndGetErrorMessage(input, "2");
        assertEquals(errorMessage, "This value should be between 5 and 70");

        errorMessage = typeValueAndGetErrorMessage(input, "5");
        assertThat(errorMessage, emptyString());

        errorMessage = typeValueAndGetErrorMessage(input, "53");
        assertThat(errorMessage, emptyString());

        errorMessage = typeValueAndGetErrorMessage(input, "70");
        assertThat(errorMessage, emptyString());

        errorMessage = typeValueAndGetErrorMessage(input, "96");
        assertEquals(errorMessage, "This value should be between 5 and 70");


        // <input type="number" min="70" max="5">
        input = form.getInputByName("min-70-max-5");

        errorMessage = typeValueAndGetErrorMessage(input, "2");
        assertThat(errorMessage, emptyString());

        errorMessage = typeValueAndGetErrorMessage(input, "53");
        assertThat(errorMessage, emptyString());

        errorMessage = typeValueAndGetErrorMessage(input, "96");
        assertThat(errorMessage, emptyString());


        // <input type="number" min="5" max="wow">
        input = form.getInputByName("min-5-max-wow");

        errorMessage = typeValueAndGetErrorMessage(input, "2");
        assertEquals(errorMessage, "This value should be larger than 5");

        errorMessage = typeValueAndGetErrorMessage(input, "5");
        assertThat(errorMessage, emptyString());

        errorMessage = typeValueAndGetErrorMessage(input, "95");
        assertThat(errorMessage, emptyString());


        // <input type="number" min="wow" max="70">
        input = form.getInputByName("min-wow-max-70");

        errorMessage = typeValueAndGetErrorMessage(input, "2");
        assertThat(errorMessage, emptyString());

        errorMessage = typeValueAndGetErrorMessage(input, "70");
        assertThat(errorMessage, emptyString());

        errorMessage = typeValueAndGetErrorMessage(input, "95");
        assertEquals(errorMessage, "This value should be less than 70");


        // <input type="number" min="wow" max="jen">
        input = form.getInputByName("min-wow-max-jen");

        errorMessage = typeValueAndGetErrorMessage(input, "2");
        assertThat(errorMessage, emptyString());

        errorMessage = typeValueAndGetErrorMessage(input, "95");
        assertThat(errorMessage, emptyString());
    }

    /**
     * Simulate human to type string into the <input>,
     *  then trigger the onchange event, thus error messages will show.
     *
     * @param input The input element
     * @param value Value to type to @input
     * @return Error message
     */
    private String typeValueAndGetErrorMessage(HtmlInput input, String value) throws IOException {
        input.reset();  // Remove the value that already in the <input>
        input.type(value);  // Type value to <input>
        input.fireEvent(Event.TYPE_CHANGE);  // The error message is triggered by change event
        return input.getParentNode().getNextSibling().getTextContent();
    }


    @TestExtension("minValidation")
    public static class MinValidation extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "minValidation";
        }
    }

    @TestExtension("maxValidation")
    public static class MaxValidation extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "maxValidation";
        }
    }

    @TestExtension("minAndMaxValidation")
    public static class MinAndMaxValidation extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "minAndMaxValidation";
        }
    }
}
