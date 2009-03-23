package hudson.scm;

import org.jvnet.hudson.test.HudsonTestCase;
import hudson.model.FreeStyleProject;

/**
 * @author Kohsuke Kawaguchi
 */
public class CVSSCMTest extends HudsonTestCase {
    /**
     * Verifies that there's no data loss.
     */
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject p = createFreeStyleProject();

        // verify values
        CVSSCM scm1 = new CVSSCM("cvsroot", "module", "branch", "cvsRsh", true, true, true, "excludedRegions");
        p.setScm(scm1);
        roundtrip(p);
        assertEquals(scm1, (CVSSCM)p.getScm());

        // all boolean fields need to be tried with two values
        scm1 = new CVSSCM("x", "y", "z", "w", false, false, false, "t");
        p.setScm(scm1);

        roundtrip(p);
        assertEquals(scm1, (CVSSCM)p.getScm());
    }

    private void roundtrip(FreeStyleProject p) throws Exception {
        submit(new WebClient().getPage(p,"configure").getFormByName("config"));
    }

    private void assertEquals(CVSSCM scm1, CVSSCM scm2) {
        assertEquals(scm1.getCvsRoot(),scm2.getCvsRoot());
        assertEquals(scm1.getAllModules(),scm2.getAllModules());
        assertEquals(scm1.getBranch(),scm2.getBranch());
        assertEquals(scm1.getCvsRsh(),scm2.getCvsRsh());
        assertEquals(scm1.getExcludedRegions(),scm2.getExcludedRegions());
        assertEquals(scm1.getCanUseUpdate(),scm2.getCanUseUpdate());
        assertEquals(scm1.isFlatten(),scm2.isFlatten());
        assertEquals(scm1.isTag(),scm2.isTag());
    }
}
