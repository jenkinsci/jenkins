package lib.form;

import static com.gargoylesoftware.htmlunit.HttpMethod.POST;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.w3c.dom.NodeList;

import java.net.URL;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExpandableTextboxTest extends HudsonTestCase {
    @Bug(2816)
    public void testMultiline() throws Exception {
        // because attribute values are normalized, it's not very easy to encode multi-line string as @value. So let's use the system message here.
        hudson.setSystemMessage("foo\nbar\nzot");
        HtmlPage page = evaluateAsHtml("<l:layout><l:main-panel><table><j:set var='instance' value='${it}'/><f:expandableTextbox field='systemMessage' /></table></l:main-panel></l:layout>");
        // System.out.println(page.getWebResponse().getContentAsString());

        NodeList textareas = page.getElementsByTagName("textarea");
        assertEquals(1, textareas.getLength());
        assertEquals(hudson.getSystemMessage(),textareas.item(0).getTextContent());
    }

    /**
     * Evaluates the literal Jelly script passed as a parameter as HTML and returns the page.
     */
    protected HtmlPage evaluateAsHtml(String jellyScript) throws Exception {
        HudsonTestCase.WebClient wc = new WebClient();

        WebRequestSettings req = new WebRequestSettings(new URL(wc.getContextPath()+"eval"), POST);
        req.setRequestBody("<j:jelly xmlns:j='jelly:core' xmlns:st='jelly:stapler' xmlns:l='/lib/layout' xmlns:f='/lib/form'>"+jellyScript+"</j:jelly>");
        Page page = wc.getPage(req);
        return (HtmlPage) page;
    }
}
