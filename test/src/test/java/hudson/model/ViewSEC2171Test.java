/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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
package hudson.model;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.javascript.host.html.HTMLElement;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author Kohsuke Kawaguchi
 */
//TODO to be merged back into ViewTest after the security release
public class ViewSEC2171Test {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("SECURITY-2171")
    public void newJob_xssPreventedInId() throws Exception {
        CustomizableTLID customizableTLID = j.jenkins.getExtensionList(TopLevelItemDescriptor.class).get(CustomizableTLID.class);
        customizableTLID.customId = "regularclass\" onclick=alert(123) other=\"";
        customizableTLID.customDisplayName = "DN-xss-id";

        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage page = wc.goTo("view/all/newJob");

        Object result = page.executeJavaScript("Array.from(document.querySelectorAll('.label')).filter(el => el.innerText.indexOf('" + customizableTLID.customDisplayName + "') !== -1)[0].parentElement.parentElement").getJavaScriptResult();
        assertThat(result, instanceOf(HTMLElement.class));
        HTMLElement resultElement = (HTMLElement) result;
        assertThat(resultElement.getAttribute("onclick", null), nullValue());
    }

    @Test
    @Issue("SECURITY-2171")
    public void newJob_xssPreventedInDisplayName() throws Exception {
        CustomizableTLID customizableTLID = j.jenkins.getExtensionList(TopLevelItemDescriptor.class).get(CustomizableTLID.class);
        customizableTLID.customId = "xss-dn";
        customizableTLID.customDisplayName = "DN <img src=x onerror=console.warn(123)>";

        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage page = wc.goTo("view/all/newJob");

        Object result = page.executeJavaScript("document.querySelector('.xss-dn .label').innerHTML").getJavaScriptResult();
        assertThat(result, instanceOf(String.class));
        String resultString = (String) result;
        assertThat(resultString, not(containsString("<")));
    }

    @Test
    public void newJob_descriptionSupportsHtml() throws Exception {
        CustomizableTLID customizableTLID = j.jenkins.getExtensionList(TopLevelItemDescriptor.class).get(CustomizableTLID.class);
        customizableTLID.customId = "html-desc";
        customizableTLID.customDescription = "Super <strong>looong</strong> description";
        
        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage page = wc.goTo("view/all/newJob");

        Object result = page.executeJavaScript("document.querySelector('.html-desc .desc strong')").getJavaScriptResult();
        assertThat(result, instanceOf(HTMLElement.class));
        assertThat(((HTMLElement) result).getTagName(), is("STRONG"));
    }
    
    @Test
    @Issue("SECURITY-2171")
    public void newJob_xssPreventedInGetIconFilePathPattern() throws Exception {
        CustomizableTLID customizableTLID = j.jenkins.getExtensionList(TopLevelItemDescriptor.class).get(CustomizableTLID.class);
        customizableTLID.customId = "xss-ifpp";
        customizableTLID.customIconClassName = null;
        customizableTLID.customIconFilePathPattern = "\"><img src=x onerror=\"alert(123)";

        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage page = wc.goTo("view/all/newJob");

        Object resultIconChildrenCount = page.executeJavaScript("document.querySelector('." + customizableTLID.customId + " .icon').children.length").getJavaScriptResult();
        assertThat(resultIconChildrenCount, instanceOf(Integer.class));
        int resultIconChildrenCountInt = (int) resultIconChildrenCount;
        assertEquals(1, resultIconChildrenCountInt);

        Object resultImgAttributesCount = page.executeJavaScript("document.querySelector('." + customizableTLID.customId + " .icon img').attributes.length").getJavaScriptResult();
        assertThat(resultImgAttributesCount, instanceOf(Integer.class));
        int resultImgAttributesCountInt = (int) resultImgAttributesCount;
        assertEquals(1, resultImgAttributesCountInt);
    }

    @Test
    public void newJob_iconClassName() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage page = wc.goTo("view/all/newJob");

        Object resultClassNames = page.executeJavaScript("document.querySelector('.hudson_model_FreeStyleProject .icon img').className").getJavaScriptResult();
        assertThat(resultClassNames, instanceOf(String.class));
        String resultClassNamesString = (String) resultClassNames;
        List<String> resultClassNamesList = Arrays.asList(resultClassNamesString.split(" "));
        assertThat(resultClassNamesList, hasItem("icon-xlg"));
        assertThat(resultClassNamesList, hasItem("icon-freestyle-project"));

        Object resultSrc = page.executeJavaScript("document.querySelector('.hudson_model_FreeStyleProject .icon img').src").getJavaScriptResult();
        assertThat(resultSrc, instanceOf(String.class));
        String resultSrcString = (String) resultSrc;
        assertThat(resultSrcString, containsString("48x48"));
        assertThat(resultSrcString, containsString("freestyleproject.png"));
    }

    @Test
    public void newJob_twoLetterIcon() throws Exception {
        CustomizableTLID customizableTLID = j.jenkins.getExtensionList(TopLevelItemDescriptor.class).get(CustomizableTLID.class);
        customizableTLID.customId = "two-letters-desc";
        customizableTLID.customDisplayName = "Two words";
        customizableTLID.customIconClassName = null;
        customizableTLID.customIconFilePathPattern = null;

        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage page = wc.goTo("view/all/newJob");

        Object result = page.executeJavaScript("document.querySelector('." + customizableTLID.customId + " .default-icon')").getJavaScriptResult();
        assertThat(result, instanceOf(HTMLElement.class));
        HTMLElement resultHtml = (HTMLElement) result;
        HTMLElement spanA = (HTMLElement) resultHtml.getFirstElementChild();
        HTMLElement spanB = (HTMLElement) resultHtml.getLastElementChild();
        assertThat(spanA.getClassName_js(), is("a"));
        assertThat(spanA.getInnerText(), is("T"));
        assertThat(spanB.getClassName_js(), is("b"));
        assertThat(spanB.getInnerText(), is("w"));
    }

    @TestExtension
    public static class CustomizableTLID extends TopLevelItemDescriptor {

        public String customId = "ID-not-yet-defined";
        public String customDisplayName = "DisplayName-not-yet-defined";
        public String customDescription = "Description-not-yet-defined";
        public String customIconFilePathPattern = "IconFilePathPattern-not-yet-defined";
        public String customIconClassName = "IconClassName-not-yet-defined";
        
        public CustomizableTLID() {
            super(FreeStyleProject.class);
        }

        @Override
        public String getId() {
            return customId;
        }

        @Override
        public String getDisplayName() {
            return customDisplayName;
        }

        @Override
        public String getDescription() {
            return customDescription;
        }

        @Override 
        public @CheckForNull String getIconFilePathPattern() {
            return customIconFilePathPattern;
        }

        @Override 
        public String getIconClassName() {
            return customIconClassName;
        }

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            throw new UnsupportedOperationException();
        }

    }
}
