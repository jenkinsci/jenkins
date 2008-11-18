package hudson.maven;

import hudson.model.Result;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SingleFileSCM;

import java.io.File;

/**
 * @author Kohsuke Kawaguchi
 */
public class RedeployPublisherTest extends HudsonTestCase {
    @Bug(2593)
    public void testBug2593() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m2 = createMavenProject();
        File repo = createTmpDir();

        // a fake build
        m2.setScm(new SingleFileSCM("pom.xml",getClass().getResource("big-artifact.pom")));
        m2.getPublishersList().add(new RedeployPublisher("",repo.toURI().toString(),true));

        MavenModuleSetBuild b = m2.scheduleBuild2(0).get();
        assertBuildStatus(Result.SUCCESS, b);

        // TODO: confirm that the artifacts use a consistent timestamp
        // TODO: we need to somehow introduce a large delay between deploy since timestamp is only second precision
        // TODO: or maybe we could use a btrace like capability to count the # of invocations?

        System.out.println(repo);
    }


}
