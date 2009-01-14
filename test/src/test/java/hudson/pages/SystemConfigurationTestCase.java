package hudson.pages;

import static com.gargoylesoftware.htmlunit.WebAssert.assertElementPresent;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.PageDecorator;
import net.sf.json.JSONObject;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Bug;
import org.kohsuke.stapler.StaplerRequest;

public class SystemConfigurationTestCase extends HudsonTestCase {

    private PageDecoratorImpl pageDecoratorImpl;

    protected void tearDown() throws Exception {
        if (pageDecoratorImpl != null) {
            PageDecorator.ALL.remove(pageDecoratorImpl);
        }
        super.tearDown();
    }
    
    /**
     * Asserts that bug#2289 is fixed.
     */
    @Bug(2289)
    public void testPageDecoratorIsListedInPage() throws Exception {
        pageDecoratorImpl = new PageDecoratorImpl();
        PageDecorator.ALL.add(pageDecoratorImpl);
        
        HtmlPage page = new WebClient().goTo("configure");
        assertXPath(page,"//tr[@name='hudson-pages-SystemConfigurationTestCase$PageDecoratorImpl']");

        HtmlForm form = page.getFormByName("config");
        form.getInputByName("_.decoratorId").setValueAttribute("this_is_a_profile");
        submit(form);
        assertEquals("The decorator field was incorrect", "this_is_a_profile", pageDecoratorImpl.getDecoratorId());
    }

    /**
     * PageDecorator for bug#2289
     */
    private static class PageDecoratorImpl extends PageDecorator {
        private String decoratorId;

        protected PageDecoratorImpl() {
            super(PageDecoratorImpl.class);
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            decoratorId = json.getString("decoratorId");
            return true;
        }

        @Override
        public String getDisplayName() {
            return "PageDecoratorImpl";
        }
        
        public String getDecoratorId() {
            return decoratorId;
        }
    }
}
