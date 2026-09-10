package lib.form;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import java.util.Objects;
import jenkins.model.Jenkins;
import org.htmlunit.WebClientUtil;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Tests that {@code f:dropdownDescriptorSelector} always captures {@code readOnlyMode} into
 * the lazily-rendered ({@code l:renderOnDemand}) fragment of a non-selected descriptor, without
 * the caller having to opt in via the taglib's {@code capture} attribute. This exercises the
 * taglib mechanism directly (see {@code test1.jelly}, which does not pass {@code capture}),
 * independently of any specific page's permission model.
 */
@WithJenkins
class DropdownDescriptorSelectorTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("JENKINS-12548")
    @Test
    void readOnlyModeIsCapturedWithoutCallerOptingIn() throws Exception {
        HtmlPage p = j.createWebClient().goTo("self/test1");

        // Triggers the same client-side fetch normally fired when the user picks
        // a non-default option from the dropdown.
        p.executeJavaScript(
                "document.getElementsBySelector('.render-on-demand').forEach(function(e) { renderOnDemand(e); })");
        WebClientUtil.waitForJSExec(p.getWebClient());

        DomElement fruitBlock = p.querySelector("div[name='fruit']");
        assertNotNull(fruitBlock, "expected the dropdown block to be rendered");

        assertNull(fruitBlock.querySelector("input[name='_.value']"),
                "the lazily-loaded, non-selected descriptor fragment must not expose an editable 'value' field"
                        + " when readOnlyMode is true, even though test1.jelly's f:dropdownDescriptorSelector"
                        + " does not pass capture=\"readOnlyMode\"");
        assertNotNull(fruitBlock.querySelector(".jenkins-not-applicable"),
                "read-only mode should instead render the standard N/A read-only placeholder");
    }

    public abstract static class Fruit implements Describable<Fruit> {
        private final String value;

        protected Fruit(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static class Apple extends Fruit {
        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public Apple(String value) {
            super(value);
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<Fruit> {}
    }

    public static class Banana extends Fruit {
        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public Banana(String value) {
            super(value);
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<Fruit> {}
    }

    @TestExtension
    public static final class RootActionImpl extends InvisibleAction implements Describable<RootActionImpl>, RootAction {

        public Fruit fruit = new Apple("seed");

        @Override
        public Descriptor<RootActionImpl> getDescriptor() {
            return Objects.requireNonNull(Jenkins.get().getDescriptorByType(DescriptorImpl.class));
        }

        @TestExtension
        public static final class DescriptorImpl extends Descriptor<RootActionImpl> {}

        @Override
        public String getUrlName() {
            return "self";
        }
    }
}
