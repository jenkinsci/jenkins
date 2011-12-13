package hudson.model;

import java.util.SortedMap;

import junit.framework.Assert;

import org.junit.Test;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.Mockito;

public class JobTest {

    private final String DISPLAY_NAME_PARAMETER_NAME = "displayName";
    private final String DEFAULT_STUB_JOB_NAME = "StubJob";
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private class StubJob extends Job {

        public StubJob() {
            super(null, DEFAULT_STUB_JOB_NAME);
        }
        
        @Override
        public boolean isBuildable() {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        protected SortedMap _getRuns() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        protected void removeRun(Run run) {
            // TODO Auto-generated method stub
            
        }
        
        /**
         * Override save so that nothig happens when setDisplayName() is called
         */
        @Override
        public void save() {
            
        }        
    }
    
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
        Assert.assertEquals(DEFAULT_STUB_JOB_NAME, j.getDisplayName());
    }
}
