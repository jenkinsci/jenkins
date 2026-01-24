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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;

import hudson.model.BallColor;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import hudson.model.StatusIcon;
import hudson.model.StockStatusIcon;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.StreamSupport;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import jenkins.util.NonLocalizable;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@WithJenkins
class IconTest  {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testIcons() throws Exception {
        HtmlPage p = j.createWebClient().goTo("testIcons");
        DomElement iconsBlock = p.getElementById("iconsBlock");
        List<DomElement> icons = StreamSupport
            .stream(iconsBlock.getChildElements().spliterator(), false)
            .toList();

        assertIconToImageOkay(icons.get(0), "/images/16x16/empty.png", "icon-empty icon-sm");
        assertIconToImageOkay(icons.get(1), "/images/24x24/empty.png", "icon-empty icon-md");
        assertIconToImageOkay(icons.get(2), "/images/32x32/empty.png", "icon-empty icon-lg");
        assertIconToImageOkay(icons.get(3), "/images/48x48/empty.png", "icon-empty icon-xlg");

        // class specs not in "normal" order...
        assertIconToImageOkay(icons.get(4), "/images/16x16/empty.png");
        assertIconToImageOkay(icons.get(5), "/images/24x24/empty.png");

        // src attribute...
        assertIconToImageOkay(icons.get(6), "/plugin/xxx/icon.png");
    }

    @TestExtension("testIcons")
    public static class TestIcons extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "testIcons";
        }
    }

    @Test
    void testBallColorTd() throws Exception {
        HtmlPage p = j.createWebClient().goTo("testBallColorTd");

        DomElement ballColorAborted = p.getElementById("ballColorAborted");
        assertThat("Aborted", is(ballColorAborted.getTextContent()));
        HtmlElement symbol = ballColorAborted.getElementsByTagName("svg").getFirst();
        assertThat("icon-md", is(symbol.getAttribute("class")));

        assertIconToSymbolOkay(symbol);

        DomElement statusIcons = p.getElementById("statusIcons");
        List<DomElement> statusIconsList = StreamSupport.stream(statusIcons.getChildElements().spliterator(), false).toList();

        assertIconToSvgOkay(statusIconsList.get(0).getFirstElementChild().getNextElementSibling(), "icon-user icon-xlg");

        assertIconToImageOkay(statusIconsList.get(1).getFirstElementChild(), "/plugin/12345/icons/s2.png");
    }

    @TestExtension("testBallColorTd")
    public static class TestBallColorTd extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "testBallColorTd";
        }

        public BallColor getBallColorAborted() {
            return BallColor.ABORTED;
        }

        public StatusIcon getStatusIcon1() {
            return new StockStatusIcon("user.svg", new NonLocalizable("A User"));
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
    }

    @Test
    void testTasks() throws Exception {
        HtmlPage p = j.createWebClient().goTo("testTasks");

        DomElement tasksDiv = p.getElementById("tasks");
        List<DomElement> taskDivs = StreamSupport.stream(tasksDiv.getChildElements().spliterator(), false).toList();

        assertIconToSymbolOkay(taskDivs.get(0).getElementsByTagName("svg").getFirst());
        // this is loading the png from cloudbees-folder plugin
        // when this is swapped to an SVG and the dep updated this test will need to change
        assertIconToSvgOkay(taskDivs.get(1).getElementsByTagName("svg").getFirst(), "icon-folder icon-md");
        assertIconToImageOkay(taskDivs.get(2).getElementsByTagName("img").getFirst(), "/images/svgs/package.svg");
        assertIconToImageOkay(taskDivs.get(3).getElementsByTagName("img").getFirst(), "/images/svgs/package.svg");
        assertIconToImageOkay(taskDivs.get(4).getElementsByTagName("img").getFirst(), "/images/svgs/package.svg");
        assertIconToSymbolOkay(taskDivs.get(5).getElementsByTagName("svg").getFirst());

        assertIconToImageOkay(taskDivs.get(6).getElementsByTagName("img").getFirst(), "/plugin/xxx/icon.png");
        assertIconToImageOkay(taskDivs.get(7).getElementsByTagName("img").getFirst(), "/plugin/xxx/icon.png");
    }

    @TestExtension("testTasks")
    public static class TestTasks extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "testTasks";
        }
    }

    private void assertIconToImageOkay(DomElement icon, String imgPath) {
        assertIconToImageOkay(icon, imgPath, null);
    }

    private void assertIconToImageOkay(DomElement icon, String imgPath, String classSpec) {
        assertThat(icon.getTagName(), is("img"));
        assertThat(icon.getAttribute("src"), endsWith(imgPath));
        if (classSpec != null) {
            assertThat(icon.getAttribute("class"), is(classSpec));
        }
    }

    private void assertIconToSvgOkay(DomElement icon, String classSpec) {
        assertThat(icon.getTagName(), is("svg"));

        if (classSpec != null) {
            assertThat(icon.getAttribute("class"), endsWith(classSpec));
        }
    }

    private void assertIconToSymbolOkay(DomElement icon) {
        assertThat("svg", is(icon.getTagName()));
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
