package hudson.tasks._ant;

import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.Ant;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SingleFileSCM;
import org.mozilla.javascript.tools.debugger.Dim;

/**
 * @author Kohsuke Kawaguchi
 */
public class AntTargetAnnotationTest extends HudsonTestCase {
    public void test1() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        Ant.AntInstallation ant = configureDefaultAnt();
        p.getBuildersList().add(new Ant("foo",ant.getName(),null,null,null));
        p.setScm(new SingleFileSCM("build.xml",getClass().getResource("simple-build.xml")));
        FreeStyleBuild b = buildAndAssertSuccess(p);

        AntTargetNote.ENABLED = true;
        try {
            HudsonTestCase.WebClient wc = createWebClient();
            HtmlPage c = wc.getPage(b, "console");
            System.out.println(c.asText());

            HtmlElement o = c.getElementById("console-outline");
            assertEquals(2,o.selectNodes(".//LI").size());
        } finally {
            AntTargetNote.ENABLED = false;
        }
    }
}
