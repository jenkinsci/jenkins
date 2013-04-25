package hudson.maven;

import hudson.maven.local_repo.PerJobLocalRepositoryLocator;
import hudson.model.Item;
import org.jvnet.hudson.test.Bug;

import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class MavenModuleSetTest extends HudsonTestCase {
    public void testConfigRoundtripLocalRepository() throws Exception {
        MavenModuleSet p = createMavenProject();
        configRoundtrip((Item) p);
        
        assertNull(p.getExplicitLocalRepository());

        // make sure it roundtrips
        PerJobLocalRepositoryLocator before = new PerJobLocalRepositoryLocator();
        p.setLocalRepository(before);
        configRoundtrip((Item)p);
        assertEqualDataBoundBeans(p.getLocalRepository(),before);
        assertTrue(before!=p.getLocalRepository());
    }

    @Bug(17402)
    public void testGetItem() throws Exception {
        assertNull(createMavenProject().getItem("invalid"));
    }

}
