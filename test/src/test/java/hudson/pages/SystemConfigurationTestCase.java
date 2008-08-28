package hudson.pages;

import static com.gargoylesoftware.htmlunit.WebAssert.*;
import hudson.model.PageDecorator;

import org.jvnet.hudson.test.HudsonTestCase;

import com.gargoylesoftware.htmlunit.html.HtmlPage;

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
    public void testPageDecoratorIsListedInPage() throws Exception {
        pageDecoratorImpl = new PageDecoratorImpl();
        PageDecorator.ALL.add(pageDecoratorImpl);
        
        HtmlPage page = new WebClient().goTo("configure");
        assertElementPresent(page, "hudson-pages-SystemConfigurationTestCase$PageDecoratorImpl");
    }

    /**
     * PageDecorator for bug#2289
     */
    private static class PageDecoratorImpl extends PageDecorator {
        protected PageDecoratorImpl() {
            super(PageDecoratorImpl.class);
        }

        @Override
        public String getDisplayName() {
            return "PageDecoratorImpl";
        }
    }
}
