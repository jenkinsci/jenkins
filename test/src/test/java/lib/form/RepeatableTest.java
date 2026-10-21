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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.htmlunit.ElementNotFoundException;
import org.htmlunit.WebClientUtil;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * @author Alan.Harder@sun.com
 */
@WithJenkins
class RepeatableTest {

    private RootActionImpl rootAction;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        rootAction = ExtensionList.lookupSingleton(RootActionImpl.class);
    }

    // ========================================================================

    private void doTestSimple(HtmlForm f, JenkinsRule.WebClient wc) throws Exception {
        f.getInputByValue("").setValue("value one");
        clickButton(wc, f, "Add", false);
        f.getInputByValue("").setValue("value two");
        clickButton(wc, f, "Add", false);
        f.getInputByValue("").setValue("value three");
        f.getInputsByName("bool").get(2).click();
        j.submit(f);
    }

    @Test
    void testSimple() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo("self/testSimple");
        HtmlForm f = p.getFormByName("config");
        clickButton(wc, f, "Add", true);
        doTestSimple(f, wc);

        assertEqualsJsonArray("[{\"bool\":false,\"txt\":\"value one\"},"
            + "{\"bool\":false,\"txt\":\"value two\"},{\"bool\":true,\"txt\":\"value three\"}]",
            rootAction.formData.get("foos"));
    }

    /**
     * Test count of buttons in form
     */
    @Test
    void testSimpleCheckNumberOfButtons() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo("self/testSimpleWithDeleteButton");
        HtmlForm f = p.getFormByName("config");
        String buttonCaption = "Add";
        assertEquals(1, getButtonsList(f, buttonCaption).size());
        clickButton(wc, f, buttonCaption, true); // click Add button
        assertEquals(1, getButtonsList(f, buttonCaption).size()); // check that second Add button is not present
        clickButton(wc, f, "Delete", true); // click Delete button
        assertEquals(1, getButtonsList(f, buttonCaption).size()); // check that only one Add button is in form
    }

    /**
     * Test count of buttons in form
     */
    @Test
    void testSimpleCheckNumberOfButtonsEnabledTopButton() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo("self/testSimpleWithDeleteButtonTopButton");
        HtmlForm f = p.getFormByName("config");
        String buttonCaption = "Add";
        assertEquals(1, getButtonsList(f, buttonCaption).size());
        clickButton(wc, f, buttonCaption, true); // click Add button
        assertEquals(2, getButtonsList(f, buttonCaption).size()); // check that second Add button was added into form
        clickButton(wc, f, "Delete", true); // click Delete button
        assertEquals(1, getButtonsList(f, buttonCaption).size()); // check that only one Add button is in form
    }

    // ========================================================================

    public static class Foo {
        public String txt;
        public boolean bool;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public Foo(String txt, boolean bool) {
            this.txt = txt;
            this.bool = bool;
        }

        @Override public String toString() { return "foo:" + txt + ':' + bool; }
    }

    private void addData() {
        rootAction.list.add(new Foo("existing one", true));
        rootAction.list.add(new Foo("existing two", false));
    }

    @Test
    void testSimple_ExistingData() throws Exception {
        addData();
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo("self/testSimple");
        HtmlForm f = p.getFormByName("config");
        clickButton(wc, f, "Add", false);
        doTestSimple(f, wc);
        assertEqualsJsonArray("[{\"bool\":true,\"txt\":\"existing one\"},"
            + "{\"bool\":false,\"txt\":\"existing two\"},{\"bool\":true,\"txt\":\"value one\"},"
            + "{\"bool\":false,\"txt\":\"value two\"},{\"bool\":false,\"txt\":\"value three\"}]",
            rootAction.formData.get("foos"));
    }

    @Test
    void testMinimum() throws Exception {
        rootAction.minimum = 3;
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo("self/testSimple");
        HtmlForm f = p.getFormByName("config");
        f.getInputByValue("").setValue("value one");
        f.getInputByValue("").setValue("value two");
        f.getInputByValue("").setValue("value three");
        assertThrows(ElementNotFoundException.class, () -> f.getInputByValue(""));
        f.getInputsByName("bool").get(2).click();
        j.submit(f);
        assertEqualsJsonArray("[{\"bool\":false,\"txt\":\"value one\"},"
            + "{\"bool\":false,\"txt\":\"value two\"},{\"bool\":true,\"txt\":\"value three\"}]",
            rootAction.formData.get("foos"));
    }

    @Test
    void testMinimum_ExistingData() throws Exception {
        addData();
        rootAction.minimum = 3;
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo("self/testSimple");
        HtmlForm f = p.getFormByName("config");
        f.getInputByValue("").setValue("new one");
        assertThrows(ElementNotFoundException.class, () -> f.getInputByValue(""));
        f.getInputsByName("bool").get(1).click();
        j.submit(f);
        assertEqualsJsonArray("[{\"bool\":true,\"txt\":\"existing one\"},"
            + "{\"bool\":true,\"txt\":\"existing two\"},{\"bool\":false,\"txt\":\"new one\"}]",
            rootAction.formData.get("foos"));
    }

    @Test
    void testNoData() throws Exception {
        rootAction.list = null;
        rootAction.defaults = null;
        JenkinsRule.WebClient wc = j.createWebClient();
        gotoAndSubmitConfig("defaultForField", wc);
        assertNull(rootAction.formData.get("list"));

        gotoAndSubmitConfig("defaultForItems", wc);
        assertNull(rootAction.formData.get("list"));
    }

    @Test
    void testItemsWithDefaults() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        assertWithDefaults("defaultForItems", wc);
    }

    @Test
    void testItemsDefaultsIgnoredIfFieldHasData() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        assertDefaultsIgnoredIfHaveData("defaultForItems", wc);
    }

    @Test
    void testFieldWithDefaults() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        assertWithDefaults("defaultForField", wc);
    }

    @Test
    void testFieldDefaultsIgnoredIfFieldHasData() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        assertDefaultsIgnoredIfHaveData("defaultForField", wc);
    }

    private void addDefaults() {
        rootAction.defaults = new ArrayList<>();
        rootAction.defaults.add(new Foo("default one", true));
        rootAction.defaults.add(new Foo("default two", false));
    }

    private void assertWithDefaults(final String viewName, final JenkinsRule.WebClient wc) throws Exception {
        rootAction.list = null;
        addDefaults();
        gotoAndSubmitConfig(viewName, wc);
        assertNotNull(rootAction.formData.get("list"));
        assertEqualsJsonArray("[{\"bool\":true,\"txt\":\"default one\"},{\"bool\":false,\"txt\":\"default two\"}]",
                rootAction.formData.get("list"));
    }

    private void assertDefaultsIgnoredIfHaveData(final String viewName, final JenkinsRule.WebClient wc) throws Exception {
        addData();
        addDefaults();
        gotoAndSubmitConfig(viewName, wc);
        assertNotNull(rootAction.formData.get("list"));
        assertEqualsJsonArray("[{\"bool\":true,\"txt\":\"existing one\"},{\"bool\":false,\"txt\":\"existing two\"}]",
                rootAction.formData.get("list"));
    }

    private void gotoAndSubmitConfig(final String viewName, final JenkinsRule.WebClient wc) throws Exception {
        HtmlPage p = wc.goTo("self/" + viewName);
        HtmlForm f = p.getFormByName("config");
        j.submit(f);
    }

    // ========================================================================

    // hudson-behavior uniquifies radiobutton names so the browser properly handles each group,
    // then converts back to original names when submitting form.
    @Test
    void testRadio() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo("self/testRadio");
        HtmlForm f = p.getFormByName("config");
        clickButton(wc, f, "Add", true);
        f.getInputByValue("").setValue("txt one");
        f.getElementsByAttribute("INPUT", "type", "radio").get(1).click();
        clickButton(wc, f, "Add", false);
        f.getInputByValue("").setValue("txt two");
        f.getElementsByAttribute("INPUT", "type", "radio").get(3).click();
        j.submit(f);
        assertEqualsJsonArray("[{\"radio\":\"two\",\"txt\":\"txt one\"},"
                + "{\"radio\":\"two\",\"txt\":\"txt two\"}]",
                     rootAction.formData.get("foos"));
    }

    public static class FooRadio {
        public String txt, radio;

        FooRadio(String txt, String radio) {
            this.txt = txt;
            this.radio = radio;
        }
    }

    @Test
    void testRadio_ExistingData() throws Exception {
        rootAction.list.add(new FooRadio("1", "one"));
        rootAction.list.add(new FooRadio("2", "two"));
        rootAction.list.add(new FooRadio("three", "one"));
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo("self/testRadio");
        HtmlForm f = p.getFormByName("config");
        clickButton(wc, f, "Add", false);
        f.getInputByValue("").setValue("txt 4");
        f.getElementsByAttribute("INPUT", "type", "radio").get(7).click();
        j.submit(f);
        assertEqualsJsonArray("[{\"radio\":\"one\",\"txt\":\"1\"},{\"radio\":\"two\",\"txt\":\"2\"},"
                + "{\"radio\":\"one\",\"txt\":\"three\"},{\"radio\":\"two\",\"txt\":\"txt 4\"}]",
                rootAction.formData.get("foos"));
    }

    // hudson-behavior uniquifies radiobutton names so the browser properly handles each group,
    // then converts back to original names when submitting form.
    @Test
    void testRadioBlock() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo("self/testRadioBlock");
        HtmlForm f = p.getFormByName("config");
        clickButton(wc, f, "Add", true);
        f.getInputByValue("").setValue("txt one");
        f.getInputByValue("").setValue("avalue do not send");
        f.getElementsByAttribute("INPUT", "type", "radio").get(1).click();
        f.getInputByValue("").setValue("bvalue");
        clickButton(wc, f, "Add", false);
        f.getInputByValue("").setValue("txt two");
        f.getElementsByAttribute("INPUT", "type", "radio").get(2).click();
        f.getInputByValue("").setValue("avalue two");
        j.submit(f);
        assertEqualsJsonArray("[{\"radio\":{\"b\":\"bvalue\",\"value\":\"two\"},\"txt\":\"txt one\"},"
                     + "{\"radio\":{\"a\":\"avalue two\",\"value\":\"one\"},\"txt\":\"txt two\"}]",
                     rootAction.formData.get("foos"));
    }

    // ========================================================================

    public static class Fruit implements ExtensionPoint, Describable<Fruit> {
        protected String name;

        private Fruit(String name) { this.name = name; }

        @Override
        public Descriptor<Fruit> getDescriptor() {
            return Jenkins.get().getDescriptor(getClass());
        }
    }

    public static class FruitDescriptor extends Descriptor<Fruit> {
        FruitDescriptor(Class<? extends Fruit> clazz) {
            super(clazz);
        }
    }

    public static class Apple extends Fruit {
        private int seeds;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor public Apple(int seeds) {
            super("Apple");
            this.seeds = seeds;
        }

        @Extension public static final FruitDescriptor D = new FruitDescriptor(Apple.class);

        @Override public String toString() { return name + " with " + seeds + " seeds"; }
    }

    public static class Banana extends Fruit {
        private boolean yellow;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor public Banana(boolean yellow) {
            super("Banana");
            this.yellow = yellow;
        }

        @Extension public static final FruitDescriptor D = new FruitDescriptor(Banana.class);

        @Override public String toString() { return (yellow ? "Yellow" : "Green") + " " + name; }
    }

    public static class Fruity {
        public Fruit fruit;
        public String word;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public Fruity(Fruit fruit, String word) {
            this.fruit = fruit;
            this.word = word;
        }

        @Override public String toString() { return fruit + " " + word; }
    }

    @Test
    void testDropdownList() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo("self/testDropdownList");
        HtmlForm f = p.getFormByName("config");
        clickButton(wc, f, "Add", true);
        f.getInputByValue("").setValue("17"); // seeds
        f.getInputByValue("").setValue("pie"); // word
        clickButton(wc, f, "Add", false);
        // select banana in 2nd select element:
        ((HtmlSelect) f.getElementsByTagName("select").get(1)).getOption(1).click();
        f.getInputsByName("yellow").get(1).click(); // checkbox
        f.getInputsByValue("").get(1).setValue("split"); // word
        String xml = f.asXml();
        rootAction.bindClass = Fruity.class;
        j.submit(f);
        assertEquals("[Apple with 17 seeds pie, Yellow Banana split]", rootAction.bindResult.toString(), rootAction.formData + "\n" + xml);
    }

    // ========================================================================

    public static class FooList {
        public String title;
        public Foo[] list;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public FooList(String title, Foo[] foo) {
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
    @Test
    void testNested() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo("self/testNested");
        HtmlForm f = p.getFormByName("config");
        try {
            clickButton(wc, f, "Add", true);
            f.getInputByValue("").setValue("title one");
            clickButton(wc, f, "Add Foo", true);
            f.getInputByValue("").setValue("txt one");
            clickButton(wc, f, "Add Foo", false);
            f.getInputByValue("").setValue("txt two");
            f.getInputsByName("bool").get(1).click();
            clickButton(wc, f, "Add", false);
            f.getInputByValue("").setValue("title two");
            f.getElementsByTagName("button").get(1).click(); // 2nd "Add Foo" button
            WebClientUtil.waitForJSExec(wc);
            f.getInputByValue("").setValue("txt 2.1");
        } catch (Exception e) {
            System.err.println("HTML at time of failure:\n" + p.getBody().asXml());
            throw e;
        }
        rootAction.bindClass = FooList.class;
        j.submit(f);
        assertEquals("[FooList:title one:[foo:txt one:false,foo:txt two:true], "
                     + "FooList:title two:[foo:txt 2.1:false]]", rootAction.bindResult.toString());
    }

    /** Tests nested repeatable and use of @DataBoundConstructor to process formData */
    @Test
    void testNestedEnabledTopButton() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo("self/testNestedTopButton");
        HtmlForm f = p.getFormByName("config");
        try {
            clickButton(wc, f, "Add", true);
            f.getInputByValue("").setValue("title one");
            clickButton(wc, f, "Add Foo", true);
            f.getInputByValue("").setValue("txt one");
            clickButton(wc, f, "Add Foo", false);
            f.getInputByValue("").setValue("txt two");
            f.getInputsByName("bool").get(1).click();
            clickButton(wc, f, "Add", false);
            f.getInputByValue("").setValue("title two");
            f.getElementsByTagName("button").get(3).click(); // 2nd "Add Foo" button
            WebClientUtil.waitForJSExec(wc);
            f.getInputByValue("").setValue("txt 2.1");
        } catch (Exception e) {
            System.err.println("HTML at time of failure:\n" + p.getBody().asXml());
            throw e;
        }
        rootAction.bindClass = FooList.class;
        j.submit(f);
        assertEquals("[FooList:title one:[foo:txt one:false,foo:txt two:true], "
                     + "FooList:title two:[foo:txt 2.1:false]]", rootAction.bindResult.toString());
    }

    /** Tests nested repeatable and use of @DataBoundConstructor to process formData */
    @Test
    void testNestedEnabledTopButtonInner() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo("self/testNestedTopButtonInner");
        HtmlForm f = p.getFormByName("config");
        try {
            clickButton(wc, f, "Add", true);
            f.getInputByValue("").setValue("title one");
            clickButton(wc, f, "Add Foo", true);
            f.getInputByValue("").setValue("txt one");
            clickButton(wc, f, "Add Foo", false);
            f.getInputByValue("").setValue("txt two");
            f.getInputsByName("bool").get(1).click();
            clickButton(wc, f, "Add", false);
            f.getInputByValue("").setValue("title two");
            f.getElementsByTagName("button").get(2).click(); // 2nd "Add Foo" button
            WebClientUtil.waitForJSExec(wc);
            f.getInputByValue("").setValue("txt 2.1");
        } catch (Exception e) {
            System.err.println("HTML at time of failure:\n" + p.getBody().asXml());
            throw e;
        }
        rootAction.bindClass = FooList.class;
        j.submit(f);
        assertEquals("[FooList:title one:[foo:txt one:false,foo:txt two:true], "
                     + "FooList:title two:[foo:txt 2.1:false]]", rootAction.bindResult.toString());
    }

    /** Tests nested repeatable and use of @DataBoundConstructor to process formData */
    @Test
    void testNestedEnabledTopButtonOuter() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo("self/testNestedTopButtonOuter");
        HtmlForm f = p.getFormByName("config");
        try {
            clickButton(wc, f, "Add", true);
            f.getInputByValue("").setValue("title one");
            clickButton(wc, f, "Add Foo", true);
            f.getInputByValue("").setValue("txt one");
            clickButton(wc, f, "Add Foo", false);
            f.getInputByValue("").setValue("txt two");
            f.getInputsByName("bool").get(1).click();
            clickButton(wc, f, "Add", false);
            f.getInputByValue("").setValue("title two");
            f.getElementsByTagName("button").get(2).click(); // 2nd "Add Foo" button
            WebClientUtil.waitForJSExec(wc);
            f.getInputByValue("").setValue("txt 2.1");
        } catch (Exception e) {
            System.err.println("HTML at time of failure:\n" + p.getBody().asXml());
            throw e;
        }
        rootAction.bindClass = FooList.class;
        j.submit(f);
        assertEquals("[FooList:title one:[foo:txt one:false,foo:txt two:true], "
                     + "FooList:title two:[foo:txt 2.1:false]]", rootAction.bindResult.toString());
    }

    private static void clickButton(JenkinsRule.WebClient wc, HtmlForm f, String caption, boolean isTopButton) throws IOException {
        getHtmlButton(f, caption, isTopButton).click();
        WebClientUtil.waitForJSExec(wc);
    }

    @Test
    void testNestedRadio() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo("self/testNestedRadio");
        HtmlForm f = p.getFormByName("config");
        try {
            clickButton(wc, f, "Add", true);
            f.getElementsByAttribute("input", "type", "radio").get(1).click(); // outer=two
            clickButton(wc, f, "Add Moo", true);
            f.getElementsByAttribute("input", "type", "radio").get(2).click(); // inner=inone
            clickButton(wc, f, "Add", false);
            f.getElementsByAttribute("input", "type", "radio").get(4).click(); // outer=one
            Thread.sleep(500);
            f.getElementsByTagName("button").get(1).click(); // 2nd "Add Moo" button
            WebClientUtil.waitForJSExec(wc);
            f.getElementsByAttribute("input", "type", "radio").get(7).click(); // inner=intwo
            f.getElementsByTagName("button").get(1).click();
            WebClientUtil.waitForJSExec(wc);
            f.getElementsByAttribute("input", "type", "radio").get(8).click(); // inner=inone
        } catch (Exception e) {
            System.err.println("HTML at time of failure:\n" + p.getBody().asXml());
            throw e;
        }
        j.submit(f);
        assertEqualsJsonArray("[{\"moo\":{\"inner\":\"inone\"},\"outer\":\"two\"},"
                + "{\"moo\":[{\"inner\":\"intwo\"},{\"inner\":\"inone\"}],\"outer\":\"one\"}]",
                rootAction.formData.get("items"));
    }

    @Test
    void testNestedRadioEnabledTopButton() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo("self/testNestedRadioTopButton");
        HtmlForm f = p.getFormByName("config");
        try {
            clickButton(wc, f, "Add", true);
            f.getElementsByAttribute("input", "type", "radio").get(1).click(); // outer=two
            clickButton(wc, f, "Add Moo", true);
            f.getElementsByAttribute("input", "type", "radio").get(2).click(); // inner=inone
            clickButton(wc, f, "Add", false);
            f.getElementsByAttribute("input", "type", "radio").get(4).click(); // outer=one
            Thread.sleep(500);
            f.getElementsByTagName("button").get(3).click(); // 2nd "Add Moo" button
            WebClientUtil.waitForJSExec(wc);
            f.getElementsByAttribute("input", "type", "radio").get(7).click(); // inner=intwo
            f.getElementsByTagName("button").get(4).click();
            WebClientUtil.waitForJSExec(wc);
            f.getElementsByAttribute("input", "type", "radio").get(8).click(); // inner=inone
        } catch (Exception e) {
            System.err.println("HTML at time of failure:\n" + p.getBody().asXml());
            throw e;
        }
        j.submit(f);
        assertEqualsJsonArray("[{\"moo\":{\"inner\":\"inone\"},\"outer\":\"two\"},"
                + "{\"moo\":[{\"inner\":\"intwo\"},{\"inner\":\"inone\"}],\"outer\":\"one\"}]",
                rootAction.formData.get("items"));
    }

    private void assertEqualsJsonArray(String golden, Object jsonArray) {
        assertEquals(JSONArray.fromObject(golden), jsonArray);
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
    private static HtmlButton getHtmlButton(HtmlForm form, String buttonCaption, boolean isTopButton) {
        List<?> buttons = getButtonsList(form, buttonCaption);
        if (buttons.size() == 1) {
            return (HtmlButton) buttons.getFirst();
        }
        return (HtmlButton) buttons.get(isTopButton ? 0 : 1);
    }

    /**
     *
     * @param form form element
     * @param buttonCaption button caption you are looking for
     * @return list of buttons
     */
    private static List<?> getButtonsList(HtmlForm form, String buttonCaption) {
        return form.getByXPath(
                String.format("//button[normalize-space(string(.)) = '%s'] | //button[@tooltip = '%s']", buttonCaption, buttonCaption)
        );
    }

    @TestExtension
    public static final class RootActionImpl extends InvisibleAction implements RootAction {

        private JSONObject formData;
        private Class<?> bindClass;
        private List<?> bindResult;
        public List<Object> list = new ArrayList<>();
        public List<Object> defaults = null;
        public Integer minimum = null;

        public DescriptorExtensionList<Fruit, Descriptor<Fruit>> getFruitDescriptors() {
            return Jenkins.get().getDescriptorList(Fruit.class);
        }

        public void doSubmitTest(StaplerRequest2 req) throws Exception {
            formData = req.getSubmittedForm();
            if (bindClass != null) {
                bindResult = req.bindJSONToList(bindClass, formData.get("items"));
            }
        }

        @Override
        public String getUrlName() {
            return "self";
        }
    }
}
