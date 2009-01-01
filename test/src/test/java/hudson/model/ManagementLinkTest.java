package hudson.model;

import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class ManagementLinkTest extends HudsonTestCase {
    /**
     * Makes sure every link works.
     */
    public void testLinks() throws Exception {
        HtmlPage page = new WebClient().goTo("manage");
        List<?> anchors = page.selectNodes("id('management-links')//*[@class='link']/a");
        assertTrue(anchors.size()>=8);
        for(HtmlAnchor e : (List<HtmlAnchor>) anchors) {
            if(e.getHrefAttribute().endsWith("reload"))
                continue;   // can't really click this
            e.click();
        }
    }
}
