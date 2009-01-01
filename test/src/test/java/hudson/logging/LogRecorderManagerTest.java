package hudson.logging;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Url;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlForm;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * @author Kohsuke Kawaguchi
 */
public class LogRecorderManagerTest extends HudsonTestCase {
    /**
     * Makes sure that the logger configuration works.
     */
    @Url("http://d.hatena.ne.jp/ssogabe/20090101/1230744150")
    public void testLoggerConfig() throws Exception {
        Logger logger = Logger.getLogger("foo.bar.zot");

        HtmlPage page = new WebClient().goTo("log/all");
        HtmlForm form = page.getFormByName("configLogger");
        form.getInputByName("name").setValueAttribute("foo.bar.zot");
        form.getSelectByName("level").getOptionByValue("finest").setSelected(true);
        submit(form);

        assertEquals(logger.getLevel(), Level.FINEST);
    }
}
