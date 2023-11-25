package lib.hudson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;

import hudson.matrix.MatrixProject;
import hudson.model.Item;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.htmlunit.html.DomNodeUtil;
import org.htmlunit.html.HtmlOption;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
public class ListScmBrowsersTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void selectBoxesUnique_FreeStyleProject() throws Exception {
        check(j.createFreeStyleProject());
    }

    @Test
    public void selectBoxesUnique_MatrixProject() throws Exception {
        check(j.jenkins.createProject(MatrixProject.class, "p"));
    }

    private void check(Item p) throws IOException, SAXException {
        HtmlPage page = j.createWebClient().getPage(p, "configure");
        List<HtmlSelect> selects = DomNodeUtil.selectNodes(page, "//select");
        assertThat(selects, not(empty()));
        for (HtmlSelect select : selects) {
            Set<String> title = new HashSet<>();
            for (HtmlOption o : select.getOptions()) {
                assertTrue("Duplicate entry: " + o.getText(), title.add(o.getText()));
            }
        }
    }
}
