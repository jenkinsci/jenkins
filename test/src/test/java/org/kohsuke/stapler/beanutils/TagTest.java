package org.kohsuke.stapler.beanutils;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.jelly.StaplerTagLibrary;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class TagTest {
    public static final String ROOT_ACTION_URL = "tagtest";
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testTagLib() throws Exception {
        final JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        { // Jelly views with basic include variants
            {
                final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/jellyStIncludeIt");
                assertThat(page.getWebResponse().getContentAsString(), containsString("Hello, World"));
            }
            {
                final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/jellyStInclude");
                assertThat(page.getWebResponse().getContentAsString(), containsString("Hello, World"));
            }
            {
                final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/jellyStIncludeClass");
                assertThat(page.getWebResponse().getContentAsString(), containsString("Hello, World"));
            }
            {
                final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/jellyStIncludeClassByName");
                assertThat(page.getWebResponse().getStatusCode(), is(500));
            }
        }
        { // Groovy views
            {
                final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyLibInclude");
                assertThat(page.getWebResponse().getContentAsString(), containsString("Hello, World"));
            }
            {
                final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyLibWithClassInclude");
                assertThat(page.getWebResponse().getContentAsString(), containsString("Hello, World"));
            }
            {
                final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyLibWithItInclude");
                assertThat(page.getWebResponse().getContentAsString(), containsString("Hello, World"));
            }
            {
                final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyNamespaceInclude");
                assertThat(page.getWebResponse().getContentAsString(), containsString("Hello, World"));
            }
            {
                final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyNamespaceWithClassInclude");
                assertThat(page.getWebResponse().getContentAsString(), containsString("Hello, World"));
            }
            {
                final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyNamespaceWithItInclude");
                assertThat(page.getWebResponse().getContentAsString(), containsString("Hello, World"));
            }
        }
        try {
            StaplerTagLibrary.DISABLE_INCLUDE_TAG_CLASS_ATTRIBUTE_REWRITING = true;

            { // Jelly views with basic include variants
                {
                    final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/jellyStIncludeIt");
                    assertThat(page.getWebResponse().getContentAsString(), containsString("Hello, World"));
                }
                {
                    final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/jellyStInclude");
                    assertThat(page.getWebResponse().getContentAsString(), containsString("Hello, World"));
                }
                {
                    final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/jellyStIncludeClass");
                    assertThat(page.getWebResponse().getContentAsString(), containsString("Hello, World"));
                }
                {
                    final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/jellyStIncludeClassByName");
                    assertThat(page.getWebResponse().getStatusCode(), is(500));
                }
            }
            { // Groovy views
                {
                    final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyLibInclude");
                    assertThat(page.getWebResponse().getContentAsString(), containsString("Hello, World"));
                }
                {
                    final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyLibWithClassInclude");
                    assertThat(page.getWebResponse().getStatusCode(), is(500));
                }
                {
                    final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyLibWithItInclude");
                    assertThat(page.getWebResponse().getContentAsString(), containsString("Hello, World"));
                }
                {
                    final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyNamespaceInclude");
                    assertThat(page.getWebResponse().getContentAsString(), containsString("Hello, World"));
                }
                {
                    final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyNamespaceWithClassInclude");
                    assertThat(page.getWebResponse().getStatusCode(), is(500));
                }
                {
                    final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyNamespaceWithItInclude");
                    assertThat(page.getWebResponse().getContentAsString(), containsString("Hello, World"));
                }
            }
        } finally {
            StaplerTagLibrary.DISABLE_INCLUDE_TAG_CLASS_ATTRIBUTE_REWRITING = false;
        }
    }

    @TestExtension
    public static class RootActionImpl extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return ROOT_ACTION_URL;
        }
    }

}
