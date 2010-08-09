package hudson.maven.reporters;

import hudson.maven.MavenBuild;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.MavenProjectActionBuilder;
import hudson.maven.reporters.SurefireArchiver.FactoryImpl;
import hudson.model.Result;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class SurefireArchiverTest extends HudsonTestCase {
    public void testSerialization() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setScm(new ExtractResourceSCM(getClass().getResource("../maven-surefire-unstable.zip")));
        m.setGoals("install");

        MavenModuleSetBuild b = m.scheduleBuild2(0).get();
        assertBuildStatus(Result.UNSTABLE, b);


        MavenBuild mb = b.getModuleLastBuilds().values().iterator().next();
        boolean foundFactory=false,foundSurefire=false;
        for (MavenProjectActionBuilder x : mb.getProjectActionBuilders()) {
            if (x instanceof FactoryImpl)
                foundFactory = true;
            if (x instanceof SurefireArchiver)
                foundSurefire = true;
        }

        assertTrue(foundFactory);
        assertFalse(foundSurefire);
    }

}
