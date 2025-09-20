package lib.hudson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class ListScmBrowsersTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void selectBoxesUnique_FreeStyleProject() throws Exception {
        check(j.createFreeStyleProject());
    }

    @Test
    void selectBoxesUnique_MatrixProject() throws Exception {
        check(j.jenkins.createProject(MatrixProject.class, "p"));
    }

    private void check(Item p) throws IOException, SAXException {
        HtmlPage page = j.createWebClient().getPage(p, "configure");
        List<HtmlSelect> selects = DomNodeUtil.selectNodes(page, "//select");
        assertThat(selects, not(empty()));
        for (HtmlSelect select : selects) {
            Set<String> title = new HashSet<>();
            for (HtmlOption o : select.getOptions()) {
                assertTrue(title.add(o.getText()), "Duplicate entry: " + o.getText());
            }
        }
    }
}
