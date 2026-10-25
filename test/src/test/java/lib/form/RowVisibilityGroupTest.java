package lib.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.ExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import java.util.List;
import java.util.Objects;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.htmlunit.html.DomNodeUtil;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlOption;
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
 * Tests the 'rowvg-start' and 'rowvg-end' CSS attributes and their effects.
 *
 * <p>
 * Some of our tags, such as &lt;optionalBlock> and &lt;dropdownList> involves grouping of sibling table rows,
 * and controlling visibility of them. So when such tags nest to each other, the visibility updates need to be
 * done carefully, or else the visibility could get out of sync with the model (imagine outer group is made visible
 * while inner group is not visible --- if all the rows are simply enumerated and visibility changed, we end up
 * making the inner group visible.)
 *
 * <p>
 * The rowVisibilityGroup object in hudson-behavior.js is used to coordinate this activity, and this test
 * ensures that it's working.
 *
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class RowVisibilityGroupTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Nested optional blocks
     */
    @Test
    void test1() throws Exception {
        HtmlPage p = j.createWebClient().goTo("self/test1");

        HtmlElement outer = DomNodeUtil.selectSingleNode(p, "//INPUT[@name='outer']");
        HtmlElement inner = DomNodeUtil.selectSingleNode(p, "//INPUT[@name='inner']");
        HtmlInput field = DomNodeUtil.selectSingleNode(p, "//INPUT[@type='text'][@name='_.field']");

        // outer gets unfolded, but inner should be still folded
        outer.click();
        assertFalse(field.isDisplayed());
        // now click inner, to reveal the field
        inner.click();
        assertTrue(field.isDisplayed());

        // folding outer should hide everything
        outer.click();
        assertFalse(field.isDisplayed());
        // but if we unfold outer, everything should be revealed because inner is already checked.
        outer.click();
        assertTrue(field.isDisplayed());
    }

    /**
     * optional block inside the dropdownDescriptorSelector
     */
    @Test
    void test2() throws Exception {
        HtmlPage p = j.createWebClient().goTo("self/test2");

        HtmlSelect s = DomNodeUtil.selectSingleNode(p, "//SELECT");
        List<HtmlOption> opts = s.getOptions();

        // those first selections will load additional HTMLs
        s.setSelectedAttribute(opts.get(0), true);
        s.setSelectedAttribute(opts.get(1), true);

        // now select back what's already loaded, to cause the existing elements to be displayed
        s.setSelectedAttribute(opts.get(0), true);

        // make sure that the inner control is still hidden
        List<HtmlInput> textboxes = DomNodeUtil.selectNodes(p, "//INPUT[@name='_.textbox2']");
        assertEquals(2, textboxes.size());
        for (HtmlInput e : textboxes)
            assertFalse(e.isDisplayed());

        // reveal the text box
        List<HtmlInput> checkboxes = DomNodeUtil.selectNodes(p, "//INPUT[@name='inner']");
        assertEquals(2, checkboxes.size());
        checkboxes.getFirst().click();
        assertTrue(textboxes.getFirst().isDisplayed());
        textboxes.getFirst().type("Budweiser");

        // toggle the selection again
        s.setSelectedAttribute(opts.get(1), true);
        s.setSelectedAttribute(opts.get(0), true);

        // make sure it's still displayed this time
        assertTrue(checkboxes.getFirst().isChecked());
        assertTrue(textboxes.getFirst().isDisplayed());

        // make sure we get what we expect
        j.submit(p.getFormByName("config"));
        RootActionImpl rootAction = ExtensionList.lookupSingleton(RootActionImpl.class);
        j.assertEqualDataBoundBeans(rootAction.beer, new Beer("", new Nested("Budweiser")));
    }

    public static class Nested {
        public String textbox2;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public Nested(String textbox2) {
            this.textbox2 = textbox2;
        }
    }

    public abstract static class Drink implements Describable<Drink> {
        public String textbox1;
        public Nested inner;

        protected Drink(String textbox1, Nested inner) {
            this.textbox1 = textbox1;
            this.inner = inner;
        }
    }

    public static class Beer extends Drink {
        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public Beer(String textbox1, Nested inner) {
            super(textbox1, inner);
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<Drink> {}
    }

    public static class Coke extends Drink {
        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public Coke(String textbox1, Nested inner) {
            super(textbox1, inner);
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<Drink> {}
    }

    @TestExtension
    public static final class RootActionImpl extends InvisibleAction implements Describable<RootActionImpl>, RootAction {

        public Drink drink;
        private Beer beer;

        public void doSubmitTest2(StaplerRequest2 req) throws Exception {
            JSONObject json = req.getSubmittedForm();
            System.out.println(json);
            beer = (Beer) req.bindJSON(Drink.class, json.getJSONObject("drink"));
        }

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
