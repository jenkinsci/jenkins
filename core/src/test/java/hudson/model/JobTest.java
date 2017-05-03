package hudson.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class JobTest {

    @Test
    public void testSetDisplayName() throws Exception {
       final String displayName = "testSetDisplayName";

       StubJob j = new StubJob();      
       // call setDisplayNameFromRequest
       j.setDisplayNameOrNull(displayName);
       
       // make sure the displayname has been set
       assertEquals(displayName, j.getDisplayName());
    }

    @Test
    public void testSetDisplayNameZeroLength() throws Exception {
        StubJob j = new StubJob();
        // call setDisplayNameFromRequest
        j.setDisplayNameOrNull("");

        // make sure the getDisplayName returns the project name
        assertEquals(StubJob.DEFAULT_STUB_JOB_NAME, j.getDisplayName());
    }
    
    @Test
    public void testHTML5Name() {
        Job j = new FreeStyleProject((ItemGroup) null, "stdName");
        Assert.assertEquals("Job name not HTML5 compliant", "stdName", j.getHtml5CompliantName());
        j = new FreeStyleProject((ItemGroup) null, "A name with Spaces");
        Assert.assertEquals("Job name not HTML5 compliant", "A_name_with_Spaces", j.getHtml5CompliantName());
        j = new FreeStyleProject((ItemGroup) null, "J'aime le français");
        Assert.assertEquals("Job name not HTML5 compliant", "J'aime_le_français", j.getHtml5CompliantName());
    }

    @Test
    public void testHTML4Name() {
        Job j = new FreeStyleProject((ItemGroup) null, "stdName");
        Assert.assertEquals("Job name not HTML4 compliant", "stdName", j.getHtml4CompliantName());
        j = new FreeStyleProject((ItemGroup) null, "A name with Spaces");
        Assert.assertEquals("Job name not HTML4 compliant", "A_name_with_Spaces", j.getHtml4CompliantName());
        j = new FreeStyleProject((ItemGroup) null, "J'aime le français 2-_.:{}$%");
        Assert.assertEquals("Job name not HTML4 compliant", "J_aime_le_fran_ais_2-_.:____", j.getHtml4CompliantName());
    }
}
