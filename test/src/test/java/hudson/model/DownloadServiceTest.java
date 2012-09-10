package hudson.model;

import hudson.model.DownloadService.Downloadable;
import net.sf.json.JSONObject;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class DownloadServiceTest extends HudsonTestCase {
    private Downloadable job;

    /**
     * Makes sure that JavaScript on the client side for handling submission works.
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // this object receives the submission.
        // to bypass the URL restriction, we'll trigger downloadService.download ourselves
        job = new Downloadable("test", "UNUSED");
        Downloadable.all().add(job);
    }

    @Override
    protected void tearDown() throws Exception {
        Downloadable.all().remove(job);
        super.tearDown();
    }

    @Bug(5536)
    public void testPost() throws Exception {
        // initially it should fail because the data doesn't have a signature
        assertNull(job.getData());
        createWebClient().goTo("/self/testPost");
        assertNull(job.getData());

        // and now it should work
        DownloadService.signatureCheck = false;
        try {
            createWebClient().goTo("/self/testPost");
            JSONObject d = job.getData();
            assertEquals(hashCode(),d.getInt("hello"));
        } finally {
            DownloadService.signatureCheck = true;
        }

        // TODO: test with a signature
    }

    /**
     * This is where the browser should hit to retrieve data.
     */
    public void doData(StaplerResponse rsp) throws IOException {
        rsp.setContentType("application/javascript");
        rsp.getWriter().println("downloadService.post('test',{'hello':"+hashCode()+"})");
    }
}
