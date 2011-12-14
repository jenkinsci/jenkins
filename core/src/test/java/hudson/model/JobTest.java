package hudson.model;


import junit.framework.Assert;

import org.junit.Test;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.Mockito;

public class JobTest {

    private final String DISPLAY_NAME_PARAMETER_NAME = "displayName";
    
    
    @Test
    public void testSetDisplayName() throws Exception {
       final String displayName = "testSetDisplayName";
       // create a mock stapler request that returns a display name
       StaplerRequest req = Mockito.mock(StaplerRequest.class);
       Mockito.when(req.getParameter(DISPLAY_NAME_PARAMETER_NAME)).thenReturn(displayName);
       
       StubJob j = new StubJob();      
       // call setDisplayNameFromRequest
       j.setDisplayNameFromRequest(req);
       
       // make sure the displayname has been set
       Assert.assertEquals(displayName, j.getDisplayName());
    }

    @Test
    public void testSetDisplayNameZeroLength() throws Exception {
        // create a mock stapler request that returns a ""
        StaplerRequest req = Mockito.mock(StaplerRequest.class);
        Mockito.when(req.getParameter(DISPLAY_NAME_PARAMETER_NAME)).thenReturn("");

        StubJob j = new StubJob();       
        // call setDisplayNameFromRequest
        j.setDisplayNameFromRequest(req);
       
        // make sure the getDisplayName returns the project name
        Assert.assertEquals(StubJob.DEFAULT_STUB_JOB_NAME, j.getDisplayName());
    }
}
