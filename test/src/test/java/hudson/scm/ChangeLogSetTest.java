package hudson.scm;

import hudson.Extension;
import hudson.MarkupText;
import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogSet.Entry;

import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.FakeChangeLogSCM.EntryImpl;
import org.jvnet.hudson.test.HudsonTestCase;

public class ChangeLogSetTest extends HudsonTestCase {

    @Bug(17084)
    public void testCatchingExceptionDuringAnnotation() throws Exception {
        EntryImpl change = new EntryImpl();
        change.setParent(ChangeLogSet.createEmpty(null)); // otherwise test would actually test only NPE thrown when accessing paret.build 
        boolean notCaught = false;
        try {
            change.getMsgAnnotated();
        } catch (Throwable t) {
            notCaught = true;
        }
        assertEquals((new EntryImpl()).getMsg(), change.getMsg());
        assertEquals(false, notCaught);
    }
    
    @Extension
    public static final class ThrowExceptionChangeLogAnnotator extends ChangeLogAnnotator {
        
        @Override
        public void annotate(AbstractBuild<?,?> build, Entry change, MarkupText text ) {
            throw new RuntimeException();
        }
        
    }
    
    @Extension
    public static final class ThrowErrorChangeLogAnnotator extends ChangeLogAnnotator {
        
        @Override
        public void annotate(AbstractBuild<?,?> build, Entry change, MarkupText text ) {
            throw new Error();
        }
        
    }
}
