package lib.hudson;

import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import hudson.model.Item;
import org.jvnet.hudson.test.HudsonTestCase;
import org.xml.sax.SAXException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class ListScmBrowsersTest extends HudsonTestCase {
    public void testSelectBoxesUnique() throws Exception {
        check(createFreeStyleProject());
    }

    public void testSelectBoxesUnique2() throws Exception {
        check(createMavenProject());
    }

    public void testSelectBoxesUnique3() throws Exception {
        check(createMatrixProject());
    }

    private void check(Item p) throws IOException, SAXException {
        HtmlPage page = new WebClient().getPage(p, "configure");
        List<HtmlSelect> selects = (List<HtmlSelect>)page.selectNodes("//select");
        assertTrue(selects.size()>0);
        for (HtmlSelect select : selects) {
            Set<String> title = new HashSet<String>();
            for(HtmlOption o : select.getOptions()) {
                assertTrue("Duplicate entry: "+o.asText(),title.add(o.asText()));
            }
        }
    }
}
