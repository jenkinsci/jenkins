package hudson.tasks._ant;

import hudson.MarkupText;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit test for the {@link AntTargetNote} class.
 */
public class AntTargetNoteTest {

    private boolean enabled;

    @Before
    public void setUp() {
        enabled = AntTargetNote.ENABLED;
    }

    @After
    public void tearDown() {
        // Restore the original setting.
        AntTargetNote.ENABLED = enabled;
    }

    @Test
    public void testAnnotateTarget() {
        assertEquals("<b class=ant-target>TARGET</b>:", annotate("TARGET:"));
    }

    @Test
    public void testAnnotateTargetContainingColon() {
        // See HUDSON-7026.
        assertEquals("<b class=ant-target>TEST:TARGET</b>:", annotate("TEST:TARGET:"));
    }

    @Test
    public void testDisabled() {
        AntTargetNote.ENABLED = false;
        assertEquals("TARGET:", annotate("TARGET:"));
    }

    private String annotate(String text) {
        MarkupText markupText = new MarkupText(text);
        new AntTargetNote().annotate(new Object(), markupText, 0);
        return markupText.toString(true);
    }
}
