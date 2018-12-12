package lib.form;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.RootAction;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.xml.sax.SAXException;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
    public static class AutocompleteOffByDefault implements RootAction {
        @Override
        public String getIconFileName() {
            return "gear2.png";
        }

        @Override
        public String getDisplayName() {
            return "AutocompleteOffByDefault";
        }

        @Override
        public String getUrlName() {
            return "autocompleteOffByDefault";
        }
    }

    @TestExtension("autocompleteOnWhenTrue")
    public static class AutocompleteOnWhenTrue implements RootAction {
        @Override
        public String getIconFileName() {
            return "gear2.png";
        }

        @Override
        public String getDisplayName() {
            return "AutocompleteOnWhenTrue";
        }

        @Override
        public String getUrlName() {
            return "autocompleteOnWhenTrue";
        }
    }

    @TestExtension("inputsCanSetAutocomplete")
    public static class InputsCanSetAutocomplete implements RootAction {
        @Override
        public String getIconFileName() {
            return "gear2.png";
        }

        @Override
        public String getDisplayName() {
            return "InputsCanSetAutocomplete";
        }

        @Override
        public String getUrlName() {
            return "inputsCanSetAutocomplete";
        }
    }
}
