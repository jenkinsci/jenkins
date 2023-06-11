package lib.form;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import java.io.IOException;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.xml.sax.SAXException;

/**
 * Tests for lib/form.jelly.
 */
public class FormTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-18435")
    public void autocompleteOffByDefault() throws IOException, SAXException {
        HtmlPage page = j.createWebClient().goTo("autocompleteOffByDefault");
        HtmlForm form = page.getFormByName("config");
        String autocomplete = form.getAttribute("autocomplete");
        assertNotNull(autocomplete);
        assertEquals("off", autocomplete);
    }

    @Test
    @Issue("JENKINS-18435")
    public void autocompleteOnWhenTrue() throws IOException, SAXException {
        HtmlPage page = j.createWebClient().goTo("autocompleteOnWhenTrue");
        HtmlForm form = page.getFormByName("config");
        String autocomplete = form.getAttribute("autocomplete");
        assertNotNull(autocomplete);
        assertEquals("on", autocomplete);
    }

    @Test
    @Issue("JENKINS-18435")
    public void inputsCanSetAutocomplete() throws IOException, SAXException {
        HtmlPage page = j.createWebClient().goTo("inputsCanSetAutocomplete");
        HtmlForm form = page.getFormByName("config");
        HtmlInput a = form.getInputByName("a");
        String autocomplete = a.getAttribute("autocomplete");
        assertNotNull(autocomplete);
        assertEquals("on", autocomplete);
    }

    @TestExtension("autocompleteOffByDefault")
    public static class AutocompleteOffByDefault extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "autocompleteOffByDefault";
        }
    }

    @TestExtension("autocompleteOnWhenTrue")
    public static class AutocompleteOnWhenTrue extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "autocompleteOnWhenTrue";
        }
    }

    @TestExtension("inputsCanSetAutocomplete")
    public static class InputsCanSetAutocomplete extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "inputsCanSetAutocomplete";
        }
    }
}
