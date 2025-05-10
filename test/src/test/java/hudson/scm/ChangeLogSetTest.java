package hudson.scm;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.Extension;
import hudson.MarkupText;
import hudson.model.AbstractBuild;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.FakeChangeLogSCM;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ChangeLogSetTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("JENKINS-17084")
    void catchingExceptionDuringAnnotation() {
        FakeChangeLogSCM.EntryImpl change = new FakeChangeLogSCM.EntryImpl();
        change.setParent(ChangeLogSet.createEmpty(null)); // otherwise test would actually test only NPE thrown when accessing parent.build
        assertDoesNotThrow(() -> {
            change.getMsgAnnotated();
        });
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
