/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Alan Harder
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
package lib.form;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import java.util.ArrayList;
import net.sf.json.JSONObject;
import org.jvnet.hudson.test.HudsonTestCase;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Alan.Harder@sun.com
 */
public class RepeatableTest extends HudsonTestCase {
    private JSONObject formData;
    public ArrayList<Object> list = new ArrayList<Object>();
    public Integer minimum = null;

    public void doSubmitTest(StaplerRequest req) throws Exception {
        formData = req.getSubmittedForm();
    }

    private void doTestSimple() throws Exception {
        HtmlPage p = createWebClient().goTo("self/testSimple");
        HtmlForm f = p.getFormByName("config");
        f.getButtonByCaption("Add").click();
        f.getInputByValue("").setValueAttribute("value one");
        f.getButtonByCaption("Add").click();
        f.getInputByValue("").setValueAttribute("value two");
        f.getButtonByCaption("Add").click();
        f.getInputByValue("").setValueAttribute("value three");
        f.getInputsByName("bool").get(2).click();
        submit(f);
    }

    public void testSimple() throws Exception {
        doTestSimple();
        assertEquals("[{\"bool\":false,\"txt\":\"value one\"},"
            + "{\"bool\":false,\"txt\":\"value two\"},{\"bool\":true,\"txt\":\"value three\"}]",
            formData.get("foos").toString());
    }

    public static class Foo {
        public String txt;
        public boolean bool;
        public Foo(String s, boolean b) { txt = s; bool = b; }
    }

    private void addData() {
        list.add(new Foo("existing one", true));
        list.add(new Foo("existing two", false));
    }

    public void testSimple_ExistingData() throws Exception {
        addData();
        doTestSimple();
        assertEquals("[{\"bool\":true,\"txt\":\"existing one\"},"
            + "{\"bool\":false,\"txt\":\"existing two\"},{\"bool\":true,\"txt\":\"value one\"},"
            + "{\"bool\":false,\"txt\":\"value two\"},{\"bool\":false,\"txt\":\"value three\"}]",
            formData.get("foos").toString());
    }

    public void testMinimum() throws Exception {
        minimum = new Integer(3);
        HtmlPage p = createWebClient().goTo("self/testSimple");
        HtmlForm f = p.getFormByName("config");
        f.getInputByValue("").setValueAttribute("value one");
        f.getInputByValue("").setValueAttribute("value two");
        f.getInputByValue("").setValueAttribute("value three");
        try { f.getInputByValue(""); fail("?"); } catch (ElementNotFoundException expected) { }
        f.getInputsByName("bool").get(2).click();
        submit(f);
        assertEquals("[{\"bool\":false,\"txt\":\"value one\"},"
            + "{\"bool\":false,\"txt\":\"value two\"},{\"bool\":true,\"txt\":\"value three\"}]",
            formData.get("foos").toString());
    }

    public void testMinimum_ExistingData() throws Exception {
        addData();
        minimum = new Integer(3);
        HtmlPage p = createWebClient().goTo("self/testSimple");
        HtmlForm f = p.getFormByName("config");
        f.getInputByValue("").setValueAttribute("new one");
        try { f.getInputByValue(""); fail("?"); } catch (ElementNotFoundException expected) { }
        f.getInputsByName("bool").get(1).click();
        submit(f);
        assertEquals("[{\"bool\":true,\"txt\":\"existing one\"},"
            + "{\"bool\":true,\"txt\":\"existing two\"},{\"bool\":false,\"txt\":\"new one\"}]",
            formData.get("foos").toString());
    }

    // hudson-behavior uniquifies radiobutton names so the browser properly handles each group,
    // then converts back to original names when submitting form.
    public void testRadio() throws Exception {
        HtmlPage p = createWebClient().goTo("self/testRadio");
        HtmlForm f = p.getFormByName("config");
        f.getButtonByCaption("Add").click();
        f.getInputByValue("").setValueAttribute("txt one");
        f.getElementsByAttribute("INPUT", "type", "radio").get(1).click();
        f.getButtonByCaption("Add").click();
        f.getInputByValue("").setValueAttribute("txt two");
        f.getElementsByAttribute("INPUT", "type", "radio").get(3).click();
        submit(f);
        assertEquals("[{\"radio\":\"two\",\"txt\":\"txt one\"},"
                     + "{\"radio\":\"two\",\"txt\":\"txt two\"}]",
                     formData.get("foos").toString());
    }

    // hudson-behavior uniquifies radiobutton names so the browser properly handles each group,
    // then converts back to original names when submitting form.
    public void testRadioBlock() throws Exception {
        HtmlPage p = createWebClient().goTo("self/testRadioBlock");
        HtmlForm f = p.getFormByName("config");
        f.getButtonByCaption("Add").click();
        f.getInputByValue("").setValueAttribute("txt one");
        f.getInputByValue("").setValueAttribute("avalue do not send");
        f.getElementsByAttribute("INPUT", "type", "radio").get(1).click();
        f.getInputByValue("").setValueAttribute("bvalue");
        f.getButtonByCaption("Add").click();
        f.getInputByValue("").setValueAttribute("txt two");
        f.getElementsByAttribute("INPUT", "type", "radio").get(2).click();
        f.getInputByValue("").setValueAttribute("avalue two");
        submit(f);
        assertEquals("[{\"radio\":{\"b\":\"bvalue\",\"value\":\"two\"},\"txt\":\"txt one\"},"
                     + "{\"radio\":{\"a\":\"avalue two\",\"value\":\"one\"},\"txt\":\"txt two\"}]",
                     formData.get("foos").toString());
    }
}