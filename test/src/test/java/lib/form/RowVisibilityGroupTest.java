package lib.form;

import com.gargoylesoftware.htmlunit.html.DomNodeUtil;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Describable;
import hudson.model.Descriptor;
import net.sf.json.JSONObject;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.util.List;

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
public class RowVisibilityGroupTest extends HudsonTestCase implements Describable<RowVisibilityGroupTest> {
    public Drink drink;
    private Beer beer;

    /**
     * Nested optional blocks
     */
    public void test1() throws Exception {
        HtmlPage p = createWebClient().goTo("self/test1");

        HtmlElement outer = (HtmlElement)DomNodeUtil.selectSingleNode(p, "//INPUT[@name='outer']");
        HtmlElement inner = (HtmlElement)DomNodeUtil.selectSingleNode(p, "//INPUT[@name='inner']");
        HtmlInput field = (HtmlInput)DomNodeUtil.selectSingleNode(p, "//INPUT[@type='text'][@name='_.field']");

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
    public void test2() throws Exception {
        HtmlPage p = createWebClient().goTo("self/test2");

        HtmlSelect s = (HtmlSelect)DomNodeUtil.selectSingleNode(p, "//SELECT");
        List<HtmlOption> opts = s.getOptions();

        // those first selections will load additional HTMLs
        s.setSelectedAttribute(opts.get(0),true);
        s.setSelectedAttribute(opts.get(1),true);

        // now select back what's already loaded, to cause the existing elements to be displayed
        s.setSelectedAttribute(opts.get(0),true);

        // make sure that the inner control is still hidden
        List<HtmlInput> textboxes = DomNodeUtil.selectNodes(p, "//INPUT[@name='_.textbox2']");
        assertEquals(2,textboxes.size());
        for (HtmlInput e : textboxes)
            assertTrue(!e.isDisplayed());

        // reveal the text box
        List<HtmlInput> checkboxes = DomNodeUtil.selectNodes(p, "//INPUT[@name='inner']");
        assertEquals(2,checkboxes.size());
        checkboxes.get(0).click();
        assertTrue(textboxes.get(0).isDisplayed());
        textboxes.get(0).type("Budweiser");

        // toggle the selection again
        s.setSelectedAttribute(opts.get(1),true);
        s.setSelectedAttribute(opts.get(0),true);

        // make sure it's still displayed this time
        assertTrue(checkboxes.get(0).isChecked());
        assertTrue(textboxes.get(0).isDisplayed());

        // make sure we get what we expect
        submit(p.getFormByName("config"));
        assertEqualDataBoundBeans(beer,new Beer("",new Nested("Budweiser")));
    }

    public void doSubmitTest2(StaplerRequest req) throws Exception {
        JSONObject json = req.getSubmittedForm();
        System.out.println(json);
        beer = (Beer)req.bindJSON(Drink.class,json.getJSONObject("drink"));
    }

    public DescriptorImpl getDescriptor() {
        return jenkins.getDescriptorByType(DescriptorImpl.class);
    }

    @TestExtension
    public static final class DescriptorImpl extends Descriptor<RowVisibilityGroupTest> {}

    public static class Nested {
        public String textbox2;

        @DataBoundConstructor
        public Nested(String textbox2) {
            this.textbox2 = textbox2;
        }
    }

    public static abstract class Drink extends AbstractDescribableImpl<Drink> {
        public String textbox1;
        public Nested inner;

        protected Drink(String textbox1, Nested inner) {
            this.textbox1 = textbox1;
            this.inner = inner;
        }
    }

    public static class Beer extends Drink {
        @DataBoundConstructor
        public Beer(String textbox1, Nested inner) {
            super(textbox1, inner);
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<Drink> {}
    }

    public static class Coke extends Drink {
        @DataBoundConstructor
        public Coke(String textbox1, Nested inner) {
            super(textbox1, inner);
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<Drink> {}
    }
}
