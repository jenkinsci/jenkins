package org.kohsuke.stapler.beanutils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.jelly.StaplerTagLibrary;

/**
 * This test suite contains tests related to Commons BeanUtils use in Stapler.
 * BeanUtils 1.9.4 and newer no longer support the 'class' attribute due to
 * potential abuse in some applications processing untrusted input. The very
 * common Stapler {@code st:include} tag has such an attribute, and these tests
 * assert that the workaround implemented in Stapler works (and that the problem
 * is fairly narrow to begin with).
 */
public class TagTest {
    private static final String ROOT_ACTION_URL = "tagtest";
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testVariousDefaultTagLibs() throws Exception {
        final JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        {
            final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/jellyDefineTagLib");
            final String content = page.getWebResponse().getContentAsString();
            assertThat(content, containsString("<div class=\"theFirstClass\">Label:theFirstLabel</div>"));
            assertThat(content, containsString("<div class=\"theSecondClass\">Label:theSecondLabel</div>"));
        }
        {
            final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/jellyViewWithReallyStaticTag");
            assertThat(page.getWebResponse().getContentAsString(), containsString("<h1 class=\"title\">It works from Jelly!</h1>"));
        }
        {
            final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyViewWithReallyStaticTag");
            assertThat(page.getWebResponse().getContentAsString(), containsString("<h1 class=\"title\">It works from Groovy!</h1>"));
        }
        {
            final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyViewWithTagLibTag");
            assertThat(page.getWebResponse().getContentAsString(), containsString("class:thisIsFromGroovy"));
        }
        {
            final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/jellyViewWithTagLibTag");
            assertThat(page.getWebResponse().getContentAsString(), containsString("class:thisIsFromJelly"));
        }
    }

    @Test
    public void testUserDefinedTagLibrary() throws Exception {
        final JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        {
            // This Jelly page, standalone, does cannot resolve the 'my' tag library
            final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/jellyWithMyTagLibClassName");
            final String content = page.getWebResponse().getContentAsString();
            assertThat(content, not(containsString(":thisIsJellyInclude")));
            assertThat(content, containsString("xmlns:my="));
        }
        {
            // With a Groovy wrapper defining the tag library so it can be resolved by class name, it works
            final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyCallingJellyBuilderJellyThenInclude");
            assertThat(page.getWebResponse().getContentAsString(), containsString(":thisIsJellyInclude"));
            // We're called from StaticTagLibrary, and assumed to be DynaTag (also, TagLibrary#getTag is needed, no returning null!)
        }
        {
            // Groovy with DynaTag works
            final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyCallingJellyBuilderJellyThenTagWithStringProperty");
            assertThat(page.getWebResponse().getContentAsString(), containsString(":stringParam"));
        }
        {
            // Groovy without DynaTag fails -- this seems to be mostly the st.include case that would fail without workaround
            final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyCallingJellyBuilderJellyThenTagWithObjectProperty");
            assertThat(page.getWebResponse().getContentAsString(), containsString("This tag does not understand"));
        }
    }


    @Test
    public void testIncludeTag() throws Exception {
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
                assertThat(page.getWebResponse().getStatusCode(), is(500)); // this has never worked, no conversion takes place (sadly)
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
                    assertThat(page.getWebResponse().getStatusCode(), is(500)); // this has never worked, no conversion takes place (sadly)
                }
            }
            { // Groovy views
                {
                    final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyLibInclude");
                    assertThat(page.getWebResponse().getContentAsString(), containsString("Hello, World"));
                }
                {
                    final HtmlPage page = wc.goTo(ROOT_ACTION_URL + "/groovyLibWithClassInclude");
                    assertThat(page.getWebResponse().getStatusCode(), is(500)); // the error we're preventing
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
                    assertThat(page.getWebResponse().getStatusCode(), is(500)); // the error we're preventing
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
