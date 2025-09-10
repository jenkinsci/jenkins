/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.ExtensionList;
import hudson.model.RootAction;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlOption;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests for lib/form/option.jelly
 */
@WithJenkins
class OptionTest {
    private static final int MODE_JELLY_REGULAR = 0;
    private static final int MODE_JELLY_FORCE_RAW = 1;

    private static final int MODE_GROOVY_REGULAR = 0;
    private static final int MODE_GROOVY_TEXT = 1;

    private static final int MODE_XML_ESCAPE = 2;
    private static final int MODE_NATIVE_OPTION = 3;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("SECURITY-624")
    void optionsAreCorrectlyEscaped() throws Exception {
        checkNonDangerousOutputCorrect_simple();
        checkNonDangerousOutputCorrect_advanced();
        checkDangerousOutputNotActive();
    }

    private void checkNonDangerousOutputCorrect_simple() throws Exception {
        String simpleText = "Simple text";
        benchOfTest_acceptEscapedCharacters(simpleText, simpleText);
    }

    private void checkNonDangerousOutputCorrect_advanced() throws Exception {
        String advancedText = "Markdown -> HTML & XHTML even with \"'_/$\\< characters";

        // all those variants are displayed normally to the user since the escaping is un-escaped by the browser
        // for display purpose (and only for display, not executing)
        String escapeForValue = escapeForValue(advancedText);
        String escapeForBody = escapeForBody(advancedText);
        String escapeForBody_alternate = escapeForBody_alternate(advancedText);

        // those variants are ugly for the user since they have some escaped visible characters
        // they are produced by too much escaping done manually
        // those tests are provided to ensure the security,
        // normally they are not used since they are displaying the value in a ugly way
        // => Markdown -&amp;gt; HTML &amp;amp; XHTML even with &quot;'_/$\&amp;lt; characters
        String escapeForBody_uglyButSafe = escapeForBody_uglyButSafe(advancedText);
        // => Markdown -&amp;gt; HTML &amp;amp; XHTML even with "'_/$\&amp;lt; characters
        String escapeForValue_uglyButSafe = escapeForValue_uglyButSafe(advancedText);

        { // lenient mode
            checkJelly(MODE_JELLY_REGULAR, advancedText, advancedText, false);
            checkGroovy(MODE_GROOVY_TEXT, advancedText, advancedText, false);
            checkGroovy(MODE_XML_ESCAPE, advancedText, advancedText, false);

            checkJelly(MODE_NATIVE_OPTION, advancedText, advancedText, advancedText, false, true, false);
            checkGroovy(MODE_NATIVE_OPTION, advancedText, advancedText, advancedText, false, true, false);

            // those ones were vulnerable before the patch, you can test that by undoing the changes
            checkJelly(MODE_JELLY_FORCE_RAW, advancedText, advancedText, false);
            checkGroovy(MODE_GROOVY_REGULAR, advancedText, advancedText, false);

            // those ones are ugly and the display value is a string with escape characters.
            checkJelly(MODE_XML_ESCAPE, advancedText, escapeForBody_alternate, advancedText, false, true, false);
            checkJelly(MODE_XML_ESCAPE, advancedText, escapeForBody_alternate, escapeForBody_alternate, false, false, true);
        }

        { // in strict mode, we need to provide the exact characters that are expected
            checkJelly(MODE_JELLY_REGULAR, advancedText, escapeForBody, escapeForValue, true);
            checkGroovy(MODE_GROOVY_TEXT, advancedText, escapeForBody, escapeForValue, true);
            checkJelly(MODE_NATIVE_OPTION, advancedText, escapeForBody, escapeForValue, true, true, false);
            checkGroovy(MODE_XML_ESCAPE, advancedText, escapeForBody, escapeForValue, true);
            checkGroovy(MODE_NATIVE_OPTION, advancedText, escapeForBody_alternate, escapeForValue, true, true, false);

            // those ones were vulnerable before the patch, you can test that by undoing the changes
            checkJelly(MODE_JELLY_FORCE_RAW, advancedText, escapeForBody, escapeForValue, true);
            checkGroovy(MODE_GROOVY_REGULAR, advancedText, escapeForBody, escapeForValue, true);

            // ugly display, was already the case, just shown here to be sure of the safety
            checkJelly(MODE_XML_ESCAPE, advancedText, escapeForBody_uglyButSafe, escapeForValue, true, true, false);
            checkJelly(MODE_XML_ESCAPE, advancedText, escapeForBody_uglyButSafe, escapeForValue_uglyButSafe, true, false, true);
        }
    }

    private String escapeForBody(String str) {
        return str
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                ;
    }

    private String escapeForBody_alternate(String str) {
        return str
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                ;
    }



    private String escapeForValue(String str) {
        return str
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                ;
    }

    private String escapeForBody_uglyButSafe(String str) {
        return str
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                // double escaping, make the text ugly but it was exactly the same case before
                // when we are using too much escaping (manual h.xmlEscape on the body)
                .replace("&", "&amp;")
                ;
    }

    private String escapeForValue_uglyButSafe(String str) {
        return str
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                // double escaping, make the text ugly but it was exactly the same case before
                // when we are using too much escaping (manual h.xmlEscape on the body)
                .replace("&", "&amp;")
                // html attribute escaping done after the double escaping
                .replace("\"", "&quot;")
                ;
    }

    private void checkDangerousOutputNotActive() throws Exception {
        // this javascript simply replace the whole document by "hacked" and so the "FLAG" will not be present
        // but it will not be executed (thanks to the escaping)
        benchOfTest_strictContains(
                "<script>function hack(){document.writeln('hacked');};document.addEventListener('DOMContentLoaded', hack, false);</script>_FLAG",
                "FLAG"
        );
    }

    private void benchOfTest_strictContains(String msg, String containsExpected) throws Exception {
        _benchOfTest(msg, containsExpected, true);
    }

    private void benchOfTest_acceptEscapedCharacters(String msg, String containsExpected) throws Exception {
        _benchOfTest(msg, containsExpected, false);
    }

    private void _benchOfTest(String msg, String containsExpected, boolean checkExactCharacters) throws Exception {
        checkJelly(MODE_JELLY_REGULAR, msg, containsExpected, checkExactCharacters);
        checkGroovy(MODE_GROOVY_TEXT, msg, containsExpected, checkExactCharacters);

        checkJelly(MODE_XML_ESCAPE, msg, containsExpected, checkExactCharacters);
        checkJelly(MODE_NATIVE_OPTION, msg, containsExpected, containsExpected, checkExactCharacters, true, false);
        checkGroovy(MODE_XML_ESCAPE, msg, containsExpected, checkExactCharacters);
        checkGroovy(MODE_NATIVE_OPTION, msg, containsExpected, containsExpected, checkExactCharacters, true, false);

        // those ones were vulnerable before the patch, you can test that by undoing the changes
        checkJelly(MODE_JELLY_FORCE_RAW, msg, containsExpected, checkExactCharacters);
        checkGroovy(MODE_GROOVY_REGULAR, msg, containsExpected, checkExactCharacters);
    }

    private void checkJelly(int mode, String msgToInject, String bothContainsExpected, boolean checkExactCharacters) throws Exception {
        checkJelly(mode, msgToInject, bothContainsExpected, bothContainsExpected, checkExactCharacters);
    }

    private void checkJelly(int mode, String msgToInject,
                            String bodyContainsExpected, String valueContainsExpected,
                            boolean checkExactCharacters) throws Exception {
        checkJelly(mode, msgToInject, bodyContainsExpected, valueContainsExpected, checkExactCharacters, true, true);
    }

    private void checkJelly(int mode, String msgToInject,
                            String bodyContainsExpected, String valueContainsExpected,
                            boolean checkExactCharacters,
                            boolean withValueTrue, boolean withValueFalse) throws Exception {
        UsingJellyView view = ExtensionList.lookupFirst(UsingJellyView.class);
        view.setMode(mode);
        view.setInjection(msgToInject);

        if (withValueTrue) {
            view.setWithValue(true);
            callPageAndCheckIfResultContainsExpected("usingJelly", bodyContainsExpected, valueContainsExpected, checkExactCharacters);
        }
        if (withValueFalse) {
            view.setWithValue(false);
            callPageAndCheckIfResultContainsExpected("usingJelly", bodyContainsExpected, valueContainsExpected, checkExactCharacters);
        }
    }

    private void checkGroovy(int mode, String msgToInject, String bothContainsExpected, boolean checkExactCharacters) throws Exception {
        checkGroovy(mode, msgToInject, bothContainsExpected, bothContainsExpected, checkExactCharacters);
    }

    private void checkGroovy(int mode, String msgToInject, String bodyContainsExpected, String valueContainsExpected, boolean checkExactCharacters) throws Exception {
        checkGroovy(mode, msgToInject, bodyContainsExpected, valueContainsExpected, checkExactCharacters, true, true);
    }

    private void checkGroovy(int mode, String msgToInject,
                             String bodyContainsExpected, String valueContainsExpected,
                             boolean checkExactCharacters,
                             boolean withValueTrue, boolean withValueFalse) throws Exception {
        UsingGroovyView view = ExtensionList.lookupFirst(UsingGroovyView.class);
        view.setMode(mode);
        view.setInjection(msgToInject);

        if (withValueTrue) {
            view.setWithValue(true);
            callPageAndCheckIfResultContainsExpected("usingGroovy", bodyContainsExpected, valueContainsExpected, checkExactCharacters);
        }
        if (withValueFalse) {
            view.setWithValue(false);
            callPageAndCheckIfResultContainsExpected("usingGroovy", bodyContainsExpected, valueContainsExpected, checkExactCharacters);
        }
    }

    private void callPageAndCheckIfResultContainsExpected(String url, String bodyContainsExpected, String valueContainsExpected, boolean checkExactCharacters) throws Exception {
        HtmlPage page = (HtmlPage) j.createWebClient().goTo(url, null);
        String responseContent = page.getWebResponse().getContentAsString();

        if (checkExactCharacters) {
            // in this mode, we check the data directly received by the response,
            // without any un-escaping done by HtmlElement

            // first value shown as value
            int indexOfValue = responseContent.indexOf(valueContainsExpected);
            assertNotEquals(-1, indexOfValue);

            // second as body
            int indexOfBody = responseContent.indexOf(bodyContainsExpected, indexOfValue + 1);

            assertNotEquals(-1, indexOfBody);

            // also check there is no "<script>" present in the answer
            int indexOfScript = responseContent.indexOf("<script>");
            assertEquals(-1, indexOfScript);
        } else {
            // in this mode, we check the content as displayed to the user, converting all the escaped characters to
            // their un-escaped equivalent, done by org.htmlunit.html.HtmlSerializer#cleanUp(String)

            HtmlElement document = page.getDocumentElement();
            DomNodeList<HtmlElement> elements = document.getElementsByTagName("option");
            assertEquals(1, elements.size());

            HtmlOption option = (HtmlOption) elements.get(0);

            // without that check, the getValue could return getText if the value is not present
            assertNotEquals(DomElement.ATTRIBUTE_NOT_DEFINED, option.getAttribute("value"));

            assertTrue(
                    option.getValueAttribute().contains(valueContainsExpected),
                    "Value attribute does not contain the expected value"
            );
            assertTrue(
                    option.getText().contains(bodyContainsExpected),
                    "Body content of the option does not contain the expected value"
            );
        }
    }

    @TestExtension("optionsAreCorrectlyEscaped")
    public static class UsingJellyView implements RootAction {
        private String injection;
        private int mode;
        private boolean withValue;

        public String getInjection() {
            return injection;
        }

        public void setInjection(String injection) {
            this.injection = injection;
        }

        public int getMode() {
            return mode;
        }

        public void setMode(int mode) {
            this.mode = mode;
        }

        public boolean isWithValue() {
            return withValue;
        }

        public void setWithValue(boolean withValue) {
            this.withValue = withValue;
        }

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return "usingJelly";
        }
    }

    @TestExtension("optionsAreCorrectlyEscaped")
    public static class UsingGroovyView implements RootAction {
        private String injection;
        private int mode;
        private boolean withValue;

        public String getInjection() {
            return injection;
        }

        public void setInjection(String injection) {
            this.injection = injection;
        }

        public int getMode() {
            return mode;
        }

        public void setMode(int mode) {
            this.mode = mode;
        }

        public boolean isWithValue() {
            return withValue;
        }

        public void setWithValue(boolean withValue) {
            this.withValue = withValue;
        }

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return "usingGroovy";
        }
    }
}
