package hudson.model;

import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.HudsonTestCase;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;

/**
 * @author Kohsuke Kawaguchi
 */
public class ViewTest extends HudsonTestCase {
    /**
     * Creating two views with the same name.
     */
    @Email("http://d.hatena.ne.jp/ssogabe/20090101/1230744150")
    public void testConflictingName() throws Exception {
        assertNull(hudson.getView("foo"));

        HtmlForm form = new WebClient().goTo("newView").getFormByName("createView");
        form.getInputByName("name").setValueAttribute("foo");
        form.getRadioButtonsByName("mode").get(0).setChecked(true);
        submit(form);
        assertNotNull(hudson.getView("foo"));

        // do it again and verify an error
        try {
            submit(form);
            fail("shouldn't be allowed to create two views of the same name.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(400,e.getStatusCode());
        }
    }
}
