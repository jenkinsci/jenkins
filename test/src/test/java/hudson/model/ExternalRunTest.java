package hudson.model;

import org.jvnet.hudson.test.HudsonTestCase;

import java.io.StringReader;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExternalRunTest extends HudsonTestCase {
    public void test1() throws Exception {
        ExternalJob p = hudson.createProject(ExternalJob.class, "test");
        ExternalRun b = p.newBuild();
        b.acceptRemoteSubmission(new StringReader(
            "<run><log content-encoding='UTF-8'>AAAAAAAA</log><result>0</result><duration>100</duration></run>"
        ));
        assertEquals(b.getResult(),Result.SUCCESS);
        assertEquals(b.getDuration(),100);

        b = p.newBuild();
        b.acceptRemoteSubmission(new StringReader(
            "<run><log content-encoding='UTF-8'>AAAAAAAA</log><result>1</result>"
        ));
        assertEquals(b.getResult(),Result.FAILURE);
    }
}
