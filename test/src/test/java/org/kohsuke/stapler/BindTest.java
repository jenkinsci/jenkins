package org.kohsuke.stapler;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

import hudson.ExtensionList;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.htmlunit.Page;
import org.htmlunit.ScriptException;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import org.kohsuke.stapler.bind.WithWellKnownURL;

@RunWith(Parameterized.class)
public class BindTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Parameterized.Parameters
    public static List<String> contexts() {
        return Arrays.asList("/jenkins", "");
    }

    public BindTest(String contextPath) {
        j.contextPath = contextPath;
    }

    @Test
    public void bindNormal() throws Exception {
        final RootActionImpl root = ExtensionList.lookupSingleton(RootActionImpl.class);
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            final HtmlPage htmlPage = wc.goTo(root.getUrlName());
            final String scriptUrl = htmlPage
                    .getElementsByTagName("script")
                    .stream()
                    .filter(it -> it.getAttribute("src").startsWith(j.contextPath + "/$stapler/bound/script" + j.contextPath + "/$stapler/bound/"))
                    .findFirst()
                    .orElseThrow()
                    .getAttribute("src");

            final Page script = wc.goTo(StringUtils.removeStart(scriptUrl, j.contextPath + "/"), "text/javascript");
            final String content = script.getWebResponse().getContentAsString();
            assertThat(content, startsWith("varname = makeStaplerProxy('" + j.contextPath + "/$stapler/bound/"));
            assertThat(content, endsWith("','test',['annotatedJsMethod1','byName1']);"));
        }
        assertThat(root.invocations, is(1));
    }

    @Test
    public void bindWithWellKnownURL() throws Exception {
        final RootActionWithWellKnownURL root = ExtensionList.lookupSingleton(RootActionWithWellKnownURL.class);
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            final HtmlPage htmlPage = wc.goTo(root.getUrlName());
            final String scriptUrl = htmlPage
                    .getElementsByTagName("script")
                    .stream()
                    .filter(it -> it.getAttribute("src").startsWith(j.contextPath + "/$stapler/bound/script" + j.contextPath + "/theWellKnownRoot?"))
                    .findFirst()
                    .orElseThrow()
                    .getAttribute("src");

            final Page script = wc.goTo(StringUtils.removeStart(scriptUrl, j.contextPath + "/"), "text/javascript");
            assertThat(script.getWebResponse().getContentAsString(), is("varname = makeStaplerProxy('" + j.contextPath + "/theWellKnownRoot','test',['annotatedJsMethod2','byName2']);"));
        }
        assertThat(root.invocations, is(1));
    }

    @Test
    public void bindWithWellKnownURLWithQuotes() throws Exception {
        final RootActionWithWellKnownURLWithQuotes root = ExtensionList.lookupSingleton(RootActionWithWellKnownURLWithQuotes.class);
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            final HtmlPage htmlPage = wc.goTo(root.getUrlName());
            final String scriptUrl = htmlPage
                    .getElementsByTagName("script")
                    .stream()
                    .filter(it -> it.getAttribute("src").startsWith(j.contextPath + "/$stapler/bound/script" + j.contextPath + "/the'Well'Known'Root'With'Quotes?"))
                    .findFirst()
                    .orElseThrow()
                    .getAttribute("src");

            final Page script = wc.goTo(StringUtils.removeStart(scriptUrl, j.contextPath + "/"), "text/javascript");
            assertThat(script.getWebResponse().getContentAsString(), is("varname = makeStaplerProxy('" + j.contextPath + "/the\\'Well\\'Known\\'Root\\'With\\'Quotes','test',['annotatedJsMethod2','byName2']);"));
        }
        assertThat(root.invocations, is(1));
    }

    @Test
    public void bindNull() throws Exception {
        final RootActionImpl root = ExtensionList.lookupSingleton(RootActionImpl.class);
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            final ScriptException exception = assertThrows(ScriptException.class, () -> wc.goTo(root.getUrlName() + "/null"));
            assertThat(exception.getFailingLineNumber(), is(2));
            assertThat(exception.getFailingColumnNumber(), is(0));
            assertThat(exception.getMessage(), containsString("TypeError: Cannot call method \"byName1\" of null"));

            final HtmlPage htmlPage = exception.getPage();
            final String scriptUrl = htmlPage.getElementsByTagName("script").stream().filter(it -> it.getAttribute("src").equals(j.contextPath + "/$stapler/bound/script/null?var=varname")).findFirst().orElseThrow().getAttribute("src");

            final Page script = wc.goTo(StringUtils.removeStart(scriptUrl, j.contextPath + "/"), "text/javascript");
            final String content = script.getWebResponse().getContentAsString();
            assertThat(content, is("varname = null;"));
        }
        assertThat(root.invocations, is(0));
    }

    @Test
    public void bindUnsafe() throws Exception {
        final RootActionImpl root = ExtensionList.lookupSingleton(RootActionImpl.class);
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            final HtmlPage htmlPage = wc.goTo(root.getUrlName() + "/unsafe-var");
            final String content = htmlPage
                    .getElementsByTagName("script")
                    .stream()
                    .filter(it -> it.getTextContent().contains("makeStaplerProxy"))
                    .findFirst()
                    .orElseThrow()
                    .getTextContent();

            assertThat(content, startsWith("window['varname']=makeStaplerProxy('" + j.contextPath + "/$stapler/bound/"));
            assertThat(content, endsWith("','test',['annotatedJsMethod1','byName1']);"));
        }
        assertThat(root.invocations, is(1));
    }

    @Test
    public void bindInlineNull() throws Exception {
        final RootActionImpl root = ExtensionList.lookupSingleton(RootActionImpl.class);
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            final HtmlPage htmlPage = wc.goTo(root.getUrlName() + "/inline-null");
            final String content = htmlPage
                    .getElementsByTagName("script")
                    .stream()
                    .filter(it -> it.getTextContent().contains("var inline"))
                    .findFirst()
                    .orElseThrow()
                    .getTextContent();

            assertThat(content, containsString("var inline = null"));
        }
        assertThat(root.invocations, is(0));
    }

    @TestExtension
    public static class RootActionImpl extends InvisibleAction implements RootAction {
        private int invocations;

        @Override
        public String getUrlName() {
            return "theRoot";
        }

        @JavaScriptMethod
        public void annotatedJsMethod1(String foo) {}

        public void jsByName1() {
            invocations++;
        }
    }

    @TestExtension
    public static class RootActionWithWellKnownURL extends InvisibleAction implements RootAction, WithWellKnownURL {
        private int invocations;

        @Override
        public String getUrlName() {
            return "theWellKnownRoot";
        }

        @Override
        public String getWellKnownUrl() {
            return "/" + getUrlName();
        }

        @JavaScriptMethod
        public void annotatedJsMethod2(String foo) {}

        public void jsByName2() {
            invocations++;
        }
    }

    @TestExtension
    public static class RootActionWithWellKnownURLWithQuotes extends InvisibleAction implements RootAction, WithWellKnownURL {
        private int invocations;

        @Override
        public String getUrlName() {
            return "the'Well'Known'Root'With'Quotes";
        }

        @Override
        public String getWellKnownUrl() {
            return "/" + getUrlName();
        }

        @JavaScriptMethod
        public void annotatedJsMethod2(String foo) {}

        public void jsByName2() {
            invocations++;
        }
    }
}
