/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
