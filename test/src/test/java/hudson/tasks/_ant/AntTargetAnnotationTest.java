package hudson.tasks._ant;

import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.Ant;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SingleFileSCM;

/**
 * @author Kohsuke Kawaguchi
 */
public class AntTargetAnnotationTest extends HudsonTestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        AntTargetNote.ENABLED = true;
    }

    public void test1() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new Ant("foo",null,null,null,null));
        p.setScm(new SingleFileSCM("build.xml",getClass().getResource("simple-build.xml")));
        FreeStyleBuild b = buildAndAssertSuccess(p);

        AntTargetNote.ENABLED = true;
        try {
            HtmlPage c = createWebClient().getPage(b, "console");
            System.out.println(c.asText());

            HtmlElement o = c.getElementById("console-outline");
            assertEquals(2,o.selectNodes("LI").size());
        } finally {
            AntTargetNote.ENABLED = false;
        }
    }
}
