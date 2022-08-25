package hudson.scm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import hudson.Extension;
import hudson.MarkupText;
import hudson.model.AbstractBuild;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FakeChangeLogSCM;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class ChangeLogSetTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-17084")
    public void catchingExceptionDuringAnnotation() {
        FakeChangeLogSCM.EntryImpl change = new FakeChangeLogSCM.EntryImpl();
        change.setParent(ChangeLogSet.createEmpty(null)); // otherwise test would actually test only NPE thrown when accessing parent.build
        try {
            change.getMsgAnnotated();
        } catch (Throwable t) {
            fail(t.getMessage());
        }
        assertEquals(new FakeChangeLogSCM.EntryImpl().getMsg(), change.getMsg());
    }

    @Extension
    public static final class ThrowExceptionChangeLogAnnotator extends ChangeLogAnnotator {
        @Override
        public void annotate(AbstractBuild<?, ?> build, ChangeLogSet.Entry change, MarkupText text) {
            throw new RuntimeException();
        }
    }

    @Extension
    public static final class ThrowErrorChangeLogAnnotator extends ChangeLogAnnotator {
        @Override
        public void annotate(AbstractBuild<?, ?> build, ChangeLogSet.Entry change, MarkupText text) {
            throw new Error();
        }
    }
}
