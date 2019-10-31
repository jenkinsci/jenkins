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
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.gargoylesoftware.htmlunit.javascript.background.JavaScriptJob;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jvnet.hudson.test.HudsonTestCase;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Alan.Harder@sun.com
 */
public class RepeatableTest extends HudsonTestCase {
    private JSONObject formData;
    private Class<?> bindClass;
    private List<?> bindResult;
    public List<Object> list = new ArrayList<Object>();
    public List<Object> defaults = null;
    public Integer minimum = null;

    public void doSubmitTest(StaplerRequest req) throws Exception {
        formData = req.getSubmittedForm();
        if (bindClass != null)
            bindResult = req.bindJSONToList(bindClass, formData.get("items"));
    }

    // ========================================================================

    private void doTestSimple(HtmlForm f) throws Exception {
        f.getInputByValue("").setValueAttribute("value one");
        getHtmlButton(f, "Add", false).click();
        f.getInputByValue("").setValueAttribute("value two");
        getHtmlButton(f, "Add", false).click();
        f.getInputByValue("").setValueAttribute("value three");
        f.getInputsByName("bool").get(2).click();
        submit(f);
    }

    public void testSimple() throws Exception {
        HtmlPage p = createWebClient().goTo("self/testSimple");
        HtmlForm f = p.getFormByName("config");
        getHtmlButton(f, "Add", true).click();
        doTestSimple(f);

        assertEqualsJsonArray("[{\"bool\":false,\"txt\":\"value one\"},"
            + "{\"bool\":false,\"txt\":\"value two\"},{\"bool\":true,\"txt\":\"value three\"}]",
            formData.get("foos"));
    }

    /**
     * Test count of buttons in form
     *
     * @throws Exception
     */
    public void testSimpleCheckNumberOfButtons() throws Exception {
        HtmlPage p = createWebClient().goTo("self/testSimpleWithDeleteButton");
        HtmlForm f = p.getFormByName("config");
        String buttonCaption = "Add";
        assertEquals(1, getButtonsList(f, buttonCaption).size());
        getHtmlButton(f, buttonCaption, true).click(); // click Add button
        waitForJavaScript(p);
        assertEquals(1, getButtonsList(f, buttonCaption).size()); // check that second Add button is not present
        getHtmlButton(f, "Delete", true).click(); // click Delete button
        waitForJavaScript(p);
        assertEquals(1, getButtonsList(f, buttonCaption).size()); // check that only one Add button is in form
    }
    
    /**
     * Test count of buttons in form
     *
     * @throws Exception
     */
    public void testSimpleCheckNumberOfButtonsEnabledTopButton() throws Exception {
        HtmlPage p = createWebClient().goTo("self/testSimpleWithDeleteButtonTopButton");
        HtmlForm f = p.getFormByName("config");
        String buttonCaption = "Add";
        assertEquals(1, getButtonsList(f, buttonCaption).size());
        getHtmlButton(f, buttonCaption, true).click(); // click Add button
        waitForJavaScript(p);
        assertEquals(2, getButtonsList(f, buttonCaption).size()); // check that second Add button was added into form
        getHtmlButton(f, "Delete", true).click(); // click Delete button
        waitForJavaScript(p);
        assertEquals(1, getButtonsList(f, buttonCaption).size()); // check that only one Add button is in form
    }

    // ========================================================================

    public static class Foo {
        public String txt;
        public boolean bool;
        @DataBoundConstructor
        public Foo(String txt, boolean bool) { this.txt = txt; this.bool = bool; }
        @Override public String toString() { return "foo:" + txt + ':' + bool; }
    }

    private void addData() {
        list.add(new Foo("existing one", true));
        list.add(new Foo("existing two", false));
    }

    public void testSimple_ExistingData() throws Exception {
        addData();
        HtmlPage p = createWebClient().goTo("self/testSimple");
        HtmlForm f = p.getFormByName("config");
        getHtmlButton(f, "Add", false).click();
        doTestSimple(f);
        assertEqualsJsonArray("[{\"bool\":true,\"txt\":\"existing one\"},"
            + "{\"bool\":false,\"txt\":\"existing two\"},{\"bool\":true,\"txt\":\"value one\"},"
            + "{\"bool\":false,\"txt\":\"value two\"},{\"bool\":false,\"txt\":\"value three\"}]",
            formData.get("foos"));
    }

    public void testMinimum() throws Exception {
        minimum = 3;
        HtmlPage p = createWebClient().goTo("self/testSimple");
        HtmlForm f = p.getFormByName("config");
        f.getInputByValue("").setValueAttribute("value one");
        f.getInputByValue("").setValueAttribute("value two");
        f.getInputByValue("").setValueAttribute("value three");
        try { f.getInputByValue(""); fail("?"); } catch (ElementNotFoundException expected) { }
        f.getInputsByName("bool").get(2).click();
        submit(f);
        assertEqualsJsonArray("[{\"bool\":false,\"txt\":\"value one\"},"
            + "{\"bool\":false,\"txt\":\"value two\"},{\"bool\":true,\"txt\":\"value three\"}]",
            formData.get("foos"));
    }

    public void testMinimum_ExistingData() throws Exception {
        addData();
        minimum = 3;
        HtmlPage p = createWebClient().goTo("self/testSimple");
        HtmlForm f = p.getFormByName("config");
        f.getInputByValue("").setValueAttribute("new one");
        try { f.getInputByValue(""); fail("?"); } catch (ElementNotFoundException expected) { }
        f.getInputsByName("bool").get(1).click();
        submit(f);
        assertEqualsJsonArray("[{\"bool\":true,\"txt\":\"existing one\"},"
            + "{\"bool\":true,\"txt\":\"existing two\"},{\"bool\":false,\"txt\":\"new one\"}]",
            formData.get("foos"));
    }
    
    public void testNoData() throws Exception {
        list = null;
        defaults = null;
        gotoAndSubmitConfig("defaultForField");
        assertNull(formData.get("list"));

        gotoAndSubmitConfig("defaultForItems");
        assertNull(formData.get("list"));
    }
    
    public void testItemsWithDefaults() throws Exception {
        assertWithDefaults("defaultForItems");
    }    

    public void testItemsDefaultsIgnoredIfFieldHasData() throws Exception {
        assertDefaultsIgnoredIfHaveData("defaultForItems");
    }    

    public void testFieldWithDefaults() throws Exception {
        assertWithDefaults("defaultForField");
    }    

    public void testFieldDefaultsIgnoredIfFieldHasData() throws Exception {
        assertDefaultsIgnoredIfHaveData("defaultForField");
    }    

    private void addDefaults() {
        defaults = new ArrayList<Object>();
        defaults.add(new Foo("default one", true));
        defaults.add(new Foo("default two", false));
    }
    
    private void assertWithDefaults(final String viewName) throws Exception {
        list = null;
        addDefaults();
        gotoAndSubmitConfig(viewName);
        assertNotNull(formData.get("list"));
        assertEqualsJsonArray("[{\"bool\":true,\"txt\":\"default one\"},{\"bool\":false,\"txt\":\"default two\"}]",
                formData.get("list"));
    }    

    private void assertDefaultsIgnoredIfHaveData(final String viewName) throws Exception {
        addData();
        addDefaults();
        gotoAndSubmitConfig(viewName);
        assertNotNull(formData.get("list"));
        assertEqualsJsonArray("[{\"bool\":true,\"txt\":\"existing one\"},{\"bool\":false,\"txt\":\"existing two\"}]",
                formData.get("list"));
    }
    
    private void gotoAndSubmitConfig(final String viewName) throws Exception {
        HtmlPage p = createWebClient().goTo("self/" + viewName);
        HtmlForm f = p.getFormByName("config");
        submit(f);
    }

    // ========================================================================

    // hudson-behavior uniquifies radiobutton names so the browser properly handles each group,
    // then converts back to original names when submitting form.
    public void testRadio() throws Exception {
        HtmlPage p = createWebClient().goTo("self/testRadio");
        HtmlForm f = p.getFormByName("config");
        getHtmlButton(f, "Add", true).click();
        f.getInputByValue("").setValueAttribute("txt one");
        f.getElementsByAttribute("INPUT", "type", "radio").get(1).click();
        getHtmlButton(f, "Add", false).click();
        f.getInputByValue("").setValueAttribute("txt two");
        f.getElementsByAttribute("INPUT", "type", "radio").get(3).click();
        submit(f);
        assertEqualsJsonArray("[{\"radio\":\"two\",\"txt\":\"txt one\"},"
                + "{\"radio\":\"two\",\"txt\":\"txt two\"}]",
                     formData.get("foos"));
    }

    public static class FooRadio {
        public String txt, radio;
        public FooRadio(String txt, String radio) { this.txt = txt; this.radio = radio; }
    }

    public void testRadio_ExistingData() throws Exception {
        list.add(new FooRadio("1", "one"));
        list.add(new FooRadio("2", "two"));
        list.add(new FooRadio("three", "one"));
        HtmlPage p = createWebClient().goTo("self/testRadio");
        HtmlForm f = p.getFormByName("config");
        getHtmlButton(f, "Add", false).click();
        f.getInputByValue("").setValueAttribute("txt 4");
        f.getElementsByAttribute("INPUT", "type", "radio").get(7).click();
        submit(f);
        assertEqualsJsonArray("[{\"radio\":\"one\",\"txt\":\"1\"},{\"radio\":\"two\",\"txt\":\"2\"},"
                + "{\"radio\":\"one\",\"txt\":\"three\"},{\"radio\":\"two\",\"txt\":\"txt 4\"}]",
                formData.get("foos"));
    }

    // hudson-behavior uniquifies radiobutton names so the browser properly handles each group,
    // then converts back to original names when submitting form.
    public void testRadioBlock() throws Exception {
        HtmlPage p = createWebClient().goTo("self/testRadioBlock");
        HtmlForm f = p.getFormByName("config");
        getHtmlButton(f, "Add", true).click();
        f.getInputByValue("").setValueAttribute("txt one");
        f.getInputByValue("").setValueAttribute("avalue do not send");
        f.getElementsByAttribute("INPUT", "type", "radio").get(1).click();
        f.getInputByValue("").setValueAttribute("bvalue");
        getHtmlButton(f, "Add", false).click();
        f.getInputByValue("").setValueAttribute("txt two");
        f.getElementsByAttribute("INPUT", "type", "radio").get(2).click();
        f.getInputByValue("").setValueAttribute("avalue two");
        submit(f);
        assertEqualsJsonArray("[{\"radio\":{\"b\":\"bvalue\",\"value\":\"two\"},\"txt\":\"txt one\"},"
                     + "{\"radio\":{\"a\":\"avalue two\",\"value\":\"one\"},\"txt\":\"txt two\"}]",
                     formData.get("foos"));
    }

    // ========================================================================

    public static class Fruit implements ExtensionPoint, Describable<Fruit> {
        protected String name;
        private Fruit(String name) { this.name = name; }

        public Descriptor<Fruit> getDescriptor() {
            return Jenkins.get().getDescriptor(getClass());
        }
    }

    public static class FruitDescriptor extends Descriptor<Fruit> {
        public FruitDescriptor(Class<? extends Fruit> clazz) {
            super(clazz);
        }
    }

    public static class Apple extends Fruit {
        private int seeds;
        @DataBoundConstructor public Apple(int seeds) { super("Apple"); this.seeds = seeds; }
        @Extension public static final FruitDescriptor D = new FruitDescriptor(Apple.class);
        @Override public String toString() { return name + " with " + seeds + " seeds"; }
    }
    public static class Banana extends Fruit {
        private boolean yellow;
        @DataBoundConstructor public Banana(boolean yellow) { super("Banana"); this.yellow = yellow; }
        @Extension public static final FruitDescriptor D = new FruitDescriptor(Banana.class);
        @Override public String toString() { return (yellow ? "Yellow" : "Green") + " " + name; }
    }

    public static class Fruity {
        public Fruit fruit;
        public String word;
        @DataBoundConstructor public Fruity(Fruit fruit, String word) {
            this.fruit = fruit;
            this.word = word;
        }
        @Override public String toString() { return fruit + " " + word; }
    }

    public DescriptorExtensionList<Fruit,Descriptor<Fruit>> getFruitDescriptors() {
        return jenkins.<Fruit,Descriptor<Fruit>>getDescriptorList(Fruit.class);
    }

    public void testDropdownList() throws Exception {
        HtmlPage p = createWebClient().goTo("self/testDropdownList");
        HtmlForm f = p.getFormByName("config");
        getHtmlButton(f, "Add", true).click();
        waitForJavaScript(p);
        f.getInputByValue("").setValueAttribute("17"); // seeds
        f.getInputByValue("").setValueAttribute("pie"); // word
        getHtmlButton(f, "Add", false).click();
        waitForJavaScript(p);
        // select banana in 2nd select element:
        ((HtmlSelect)f.getElementsByTagName("select").get(1)).getOption(1).click();
        f.getInputsByName("yellow").get(1).click(); // checkbox
        f.getInputsByValue("").get(1).setValueAttribute("split"); // word
        String xml = f.asXml();
        bindClass = Fruity.class;
        submit(f);
        assertEquals(formData + "\n" + xml,
                     "[Apple with 17 seeds pie, Yellow Banana split]", bindResult.toString());
    }

    // ========================================================================

    public static class FooList {
        public String title;
        public Foo[] list = new Foo[0];
        @DataBoundConstructor public FooList(String title, Foo[] foo) {
            this.title = title;
            this.list = foo;
        }
        @Override public String toString() {
            StringBuilder buf = new StringBuilder("FooList:" + title + ":[");
            for (int i = 0; i < list.length; i++) {
                if (i > 0) buf.append(',');
                buf.append(list[i].toString());
            }
            buf.append(']');
            return buf.toString();
        }
    }

    /** Tests nested repeatable and use of @DataBoundConstructor to process formData */
    public void testNested() throws Exception {
        HtmlPage p = createWebClient().goTo("self/testNested");
        HtmlForm f = p.getFormByName("config");
        try {
            clickButton(p, f, "Add", true);
            f.getInputByValue("").setValueAttribute("title one");
            clickButton(p,f,"Add Foo", true);
            f.getInputByValue("").setValueAttribute("txt one");
            clickButton(p,f,"Add Foo", false);
            f.getInputByValue("").setValueAttribute("txt two");
            f.getInputsByName("bool").get(1).click();
            clickButton(p, f, "Add", false);
            f.getInputByValue("").setValueAttribute("title two");
            f.getElementsByTagName("button").get(1).click(); // 2nd "Add Foo" button
            f.getInputByValue("").setValueAttribute("txt 2.1");
        } catch (Exception e) {
            System.err.println("HTML at time of failure:\n" + p.getBody().asXml());
            throw e;
        }
        bindClass = FooList.class;
        submit(f);
        assertEquals("[FooList:title one:[foo:txt one:false,foo:txt two:true], "
                     + "FooList:title two:[foo:txt 2.1:false]]", bindResult.toString());
    }

    /** Tests nested repeatable and use of @DataBoundConstructor to process formData */
    public void testNestedEnabledTopButton() throws Exception {
        HtmlPage p = createWebClient().goTo("self/testNestedTopButton");
        HtmlForm f = p.getFormByName("config");
        try {
            clickButton(p, f, "Add", true);
            f.getInputByValue("").setValueAttribute("title one");
            clickButton(p,f,"Add Foo", true);
            f.getInputByValue("").setValueAttribute("txt one");
            clickButton(p,f,"Add Foo", false);
            f.getInputByValue("").setValueAttribute("txt two");
            f.getInputsByName("bool").get(1).click();
            clickButton(p, f, "Add", false);
            f.getInputByValue("").setValueAttribute("title two");
            f.getElementsByTagName("button").get(3).click(); // 2nd "Add Foo" button
            f.getInputByValue("").setValueAttribute("txt 2.1");
        } catch (Exception e) {
            System.err.println("HTML at time of failure:\n" + p.getBody().asXml());
            throw e;
        }
        bindClass = FooList.class;
        submit(f);
        assertEquals("[FooList:title one:[foo:txt one:false,foo:txt two:true], "
                     + "FooList:title two:[foo:txt 2.1:false]]", bindResult.toString());
    }

    /** Tests nested repeatable and use of @DataBoundConstructor to process formData */
    public void testNestedEnabledTopButtonInner() throws Exception {
        HtmlPage p = createWebClient().goTo("self/testNestedTopButtonInner");
        HtmlForm f = p.getFormByName("config");
        try {
            clickButton(p, f, "Add", true);
            f.getInputByValue("").setValueAttribute("title one");
            clickButton(p,f,"Add Foo", true);
            f.getInputByValue("").setValueAttribute("txt one");
            clickButton(p,f,"Add Foo", false);
            f.getInputByValue("").setValueAttribute("txt two");
            f.getInputsByName("bool").get(1).click();
            clickButton(p, f, "Add", false);
            f.getInputByValue("").setValueAttribute("title two");
            f.getElementsByTagName("button").get(2).click(); // 2nd "Add Foo" button
            f.getInputByValue("").setValueAttribute("txt 2.1");
        } catch (Exception e) {
            System.err.println("HTML at time of failure:\n" + p.getBody().asXml());
            throw e;
        }
        bindClass = FooList.class;
        submit(f);
        assertEquals("[FooList:title one:[foo:txt one:false,foo:txt two:true], "
                     + "FooList:title two:[foo:txt 2.1:false]]", bindResult.toString());
    }

    /** Tests nested repeatable and use of @DataBoundConstructor to process formData */
    public void testNestedEnabledTopButtonOuter() throws Exception {
        HtmlPage p = createWebClient().goTo("self/testNestedTopButtonOuter");
        HtmlForm f = p.getFormByName("config");
        try {
            clickButton(p, f, "Add", true);
            f.getInputByValue("").setValueAttribute("title one");
            clickButton(p,f,"Add Foo", true);
            f.getInputByValue("").setValueAttribute("txt one");
            clickButton(p,f,"Add Foo", false);
            f.getInputByValue("").setValueAttribute("txt two");
            f.getInputsByName("bool").get(1).click();
            clickButton(p, f, "Add", false);
            f.getInputByValue("").setValueAttribute("title two");
            f.getElementsByTagName("button").get(2).click(); // 2nd "Add Foo" button
            f.getInputByValue("").setValueAttribute("txt 2.1");
        } catch (Exception e) {
            System.err.println("HTML at time of failure:\n" + p.getBody().asXml());
            throw e;
        }
        bindClass = FooList.class;
        submit(f);
        assertEquals("[FooList:title one:[foo:txt one:false,foo:txt two:true], "
                     + "FooList:title two:[foo:txt 2.1:false]]", bindResult.toString());
    }

    private void clickButton(HtmlPage p, HtmlForm f, String caption, boolean isTopButton) throws IOException {
        getHtmlButton(f, caption, isTopButton).click();
        waitForJavaScript(p);
    }

    public void testNestedRadio() throws Exception {
        HtmlPage p = createWebClient().goTo("self/testNestedRadio");
        HtmlForm f = p.getFormByName("config");
        try {
            clickButton(p, f, "Add", true);
            f.getElementsByAttribute("input", "type", "radio").get(1).click(); // outer=two
            clickButton(p, f, "Add Moo", true);
            f.getElementsByAttribute("input", "type", "radio").get(2).click(); // inner=inone
            clickButton(p, f, "Add", false);
            f.getElementsByAttribute("input", "type", "radio").get(4).click(); // outer=one
            Thread.sleep(500);
            f.getElementsByTagName("button").get(1).click(); // 2nd "Add Moo" button
            waitForJavaScript(p);
            f.getElementsByAttribute("input", "type", "radio").get(7).click(); // inner=intwo
            f.getElementsByTagName("button").get(1).click();
            waitForJavaScript(p);
            f.getElementsByAttribute("input", "type", "radio").get(8).click(); // inner=inone
        } catch (Exception e) {
            System.err.println("HTML at time of failure:\n" + p.getBody().asXml());
            throw e;
        }
        submit(f);
        assertEqualsJsonArray("[{\"moo\":{\"inner\":\"inone\"},\"outer\":\"two\"},"
                + "{\"moo\":[{\"inner\":\"intwo\"},{\"inner\":\"inone\"}],\"outer\":\"one\"}]",
                formData.get("items"));
    }

    public void testNestedRadioEnabledTopButton() throws Exception {
        HtmlPage p = createWebClient().goTo("self/testNestedRadioTopButton");
        HtmlForm f = p.getFormByName("config");
        try {
            clickButton(p, f, "Add", true);
            f.getElementsByAttribute("input", "type", "radio").get(1).click(); // outer=two
            clickButton(p, f, "Add Moo", true);
            f.getElementsByAttribute("input", "type", "radio").get(2).click(); // inner=inone
            clickButton(p, f, "Add", false);
            f.getElementsByAttribute("input", "type", "radio").get(4).click(); // outer=one
            Thread.sleep(500);
            f.getElementsByTagName("button").get(3).click(); // 2nd "Add Moo" button
            waitForJavaScript(p);
            f.getElementsByAttribute("input", "type", "radio").get(7).click(); // inner=intwo
            f.getElementsByTagName("button").get(4).click();
            waitForJavaScript(p);
            f.getElementsByAttribute("input", "type", "radio").get(8).click(); // inner=inone
        } catch (Exception e) {
            System.err.println("HTML at time of failure:\n" + p.getBody().asXml());
            throw e;
        }
        submit(f);
        assertEqualsJsonArray("[{\"moo\":{\"inner\":\"inone\"},\"outer\":\"two\"},"
                + "{\"moo\":[{\"inner\":\"intwo\"},{\"inner\":\"inone\"}],\"outer\":\"one\"}]",
                formData.get("items"));
    }

    private void assertEqualsJsonArray(String golden, Object jsonArray) {
        assertEquals(JSONArray.fromObject(golden),jsonArray);
    }

    /**
     * YUI internally partially relies on setTimeout/setInterval when we add a new chunk of HTML
     * to the page. So wait for the completion of it.
     *
     * <p>
     * To see where such asynchronous activity is happening, set a breakpoint to
     * {@link JavaScriptJobManagerImpl#addJob(JavaScriptJob, Page)} and look at the call stack.
     * Also see {@link #jsDebugger} at that time to see the JavaScript callstack.
     */
    private void waitForJavaScript(HtmlPage p) {
        p.getEnclosingWindow().getJobManager().waitForJobsStartingBefore(500);
    }

    /**
     * Get one of HTML button from a form
     *
     * @param form form element
     * @param buttonCaption button caption you are looking for
     * @param isTopButton true to get top (first) Add button (buttonCaption) - adds form block
     * on top of a form, false to get second Add button - adds form block on
     * bottom of a form
     * if there is only one button, it will be returned
     * @return HTMLButton - one of Add buttons
     */
    private HtmlButton getHtmlButton(HtmlForm form, String buttonCaption, boolean isTopButton) {
        List<?> buttons = getButtonsList(form, buttonCaption);
        if (buttons.size() == 1) {
            return (HtmlButton) buttons.get(0);
        }
        return (HtmlButton) buttons.get(isTopButton ? 0 : 1);
    }

    /**
     *
     * @param form form element
     * @param buttonCaption button caption you are looking for
     * @return list of buttons
     */
    private List<?> getButtonsList(HtmlForm form, String buttonCaption) {
        return form.getByXPath("//button[text() = '" + buttonCaption + "']");
    }

}