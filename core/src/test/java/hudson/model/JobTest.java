package hudson.model;


import org.junit.Assert;

import org.junit.Test;

public class JobTest {

    @Test
    public void testSetDisplayName() throws Exception {
       final String displayName = "testSetDisplayName";

       StubJob j = new StubJob();      
       // call setDisplayNameFromRequest
       j.setDisplayNameOrNull(displayName);
       
       // make sure the displayname has been set
       Assert.assertEquals(displayName, j.getDisplayName());
    }

    @Test
    public void testSetDisplayNameZeroLength() throws Exception {
        StubJob j = new StubJob();
        // call setDisplayNameFromRequest
        j.setDisplayNameOrNull("");

        // make sure the getDisplayName returns the project name
        Assert.assertEquals(StubJob.DEFAULT_STUB_JOB_NAME, j.getDisplayName());
    }
}
