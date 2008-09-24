package hudson.scm;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.PresetData;
import static org.jvnet.hudson.test.recipes.PresetData.DataSet.ANONYMOUS_READONLY;

import java.io.File;

/**
 * @author Kohsuke Kawaguchi
 */
public class SubversionSCMTest extends HudsonTestCase {
    @PresetData(ANONYMOUS_READONLY)
    @Bug(-1) // TODO
    public void testTaggingPermission() throws Exception {
        // create a build
        File svnRepo = new CopyExisting(getClass().getResource("/svn-repo.zip")).allocate();
        FreeStyleProject p = createFreeStyleProject();
        p.setScm(new SubversionSCM(
                new String[]{"file://"+svnRepo+"/trunk/a"},
                new String[]{null},
                true, null
        ));
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        System.out.println(b.getLog());
        assertEquals(Result.SUCCESS,b.getResult());

        SubversionTagAction action = b.getAction(SubversionTagAction.class);
        assertFalse(b.hasPermission(action.getPermission()));

        // TODO: test permission
    }
}
