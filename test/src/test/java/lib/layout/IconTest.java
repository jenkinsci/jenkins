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
package lib.layout;

import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.google.common.collect.Lists;
import hudson.model.BallColor;
import hudson.model.StatusIcon;
import hudson.model.StockStatusIcon;
import jenkins.util.NonLocalizable;
import org.jvnet.hudson.test.HudsonTestCase;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.List;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class IconTest extends HudsonTestCase  {

    // Tried using JenkinsRule etc in this test but there must be some magic
    // that I missed... I was getting 404s and other errors.

    public void testIcons() throws Exception {
        HtmlPage p = createWebClient().goTo("self/01_testIcons");
        DomElement iconsBlock = p.getElementById("iconsBlock");
        List<DomElement> icons = Lists.newArrayList(iconsBlock.getChildElements());

        assertIconToImageOkay(icons.get(0), "/images/16x16/aborted.png", "icon-aborted icon-sm");
        assertIconToImageOkay(icons.get(1), "/images/24x24/aborted.png", "icon-aborted icon-md");
        assertIconToImageOkay(icons.get(2), "/images/32x32/aborted.png", "icon-aborted icon-lg");
        assertIconToImageOkay(icons.get(3), "/images/48x48/aborted.png", "icon-aborted icon-xlg");

        // class specs not in "normal" order...
        assertIconToImageOkay(icons.get(4), "/images/16x16/aborted.png");
        assertIconToImageOkay(icons.get(5), "/images/24x24/aborted.png");

        // src attribute...
        assertIconToImageOkay(icons.get(6), "/plugin/xxx/icon.png");
    }

    public void testBallColorTd() throws Exception {
        HtmlPage p = createWebClient().goTo("self/02_testBallColorTd");

        DomElement ballColorAborted = p.getElementById("ballColorAborted");
        List<DomElement> ballIcons = Lists.newArrayList(ballColorAborted.getChildElements());
        assertIconToImageOkay(ballIcons.get(0), "/images/32x32/aborted.png", "icon-aborted icon-lg");

        DomElement statusIcons = p.getElementById("statusIcons");
        List<DomElement> statusIconsList = Lists.newArrayList(statusIcons.getChildElements());
        assertIconToImageOkay(statusIconsList.get(0), "/images/32x32/folder.png", "icon-folder icon-lg");

        assertIconToImageOkay(statusIconsList.get(1), "/plugin/12345/icons/s2.png");
    }

    public void testTasks() throws Exception {
        HtmlPage p = createWebClient().goTo("self/03_testTask");

        DomElement tasksDiv = p.getElementById("tasks");
        List<DomElement> taskDivs = Lists.newArrayList(tasksDiv.getChildElements());

        assertIconToImageOkay(taskDivs.get(0).getElementsByTagName("img").get(0), "/images/24x24/up.png", "icon-up icon-md");
        assertIconToImageOkay(taskDivs.get(1).getElementsByTagName("img").get(0), "/images/24x24/folder.png", "icon-folder icon-md");
        assertIconToImageOkay(taskDivs.get(2).getElementsByTagName("img").get(0), "/images/16x16/blue.png", "icon-blue icon-sm");
        assertIconToImageOkay(taskDivs.get(3).getElementsByTagName("img").get(0), "/images/16x16/blue.png", "icon-blue icon-sm");
        assertIconToImageOkay(taskDivs.get(4).getElementsByTagName("img").get(0), "/images/16x16/blue.png", "icon-blue icon-sm");

        assertIconToImageOkay(taskDivs.get(5).getElementsByTagName("img").get(0), "/plugin/xxx/icon.png");
        assertIconToImageOkay(taskDivs.get(6).getElementsByTagName("img").get(0), "/plugin/xxx/icon.png");
    }

    public BallColor getBallColorAborted() {
        return BallColor.ABORTED;
    }

    public StatusIcon getStatusIcon1() {
        return new StockStatusIcon("folder.png", new NonLocalizable("A Folder"));
    }

    public StatusIcon getStatusIcon2() {
        return new StatusIcon() {
            @Override
            public String getImageOf(String size) {
                return "/plugin/12345/icons/s2.png";
            }
            @Override
            public String getDescription() {
                return "Unknown icon";
            }
        };
    }

    private void assertIconToImageOkay(DomElement icon, String imgPath) {
        assertIconToImageOkay(icon, imgPath, null);
    }

    private void assertIconToImageOkay(DomElement icon, String imgPath, String classSpec) {
        assertEquals("img", icon.getTagName());
        assertTrue(icon.getAttribute("src").endsWith(imgPath));
        if (classSpec != null) {
            assertEquals(classSpec, icon.getAttribute("class"));
        }
    }

    private void dump(HtmlElement element) throws TransformerException {
        System.out.println("****");
        System.out.println(toString(element));
        System.out.println("****");
    }

    private String toString(HtmlElement element) throws TransformerException {
        StringWriter writer = new StringWriter();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new DOMSource(element), new StreamResult(writer));
        return writer.toString();
    }
}
