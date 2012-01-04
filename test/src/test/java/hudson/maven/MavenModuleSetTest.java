package hudson.maven;

import hudson.maven.local_repo.PerJobLocalRepositoryLocator;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class MavenModuleSetTest extends HudsonTestCase {
    public void testConfigRoundtripLocalRepository() throws Exception {
        MavenModuleSet p = createMavenProject();
        configRoundtrip(p);
        
        assertNull(p.getExplicitLocalRepository());

        // make sure it roundtrips
        PerJobLocalRepositoryLocator before = new PerJobLocalRepositoryLocator();
        p.setLocalRepository(before);
        configRoundtrip(p);
        assertEqualDataBoundBeans(p.getLocalRepository(),before);
        assertTrue(before!=p.getLocalRepository());
    }
}
