package hudson.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.console.AnnotatedLargeText;
import hudson.security.ACL;
import hudson.security.Permission;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * @author Jerome Lacoste
 */
class TaskActionTest {

    private static class MyTaskThread extends TaskThread {
        MyTaskThread(TaskAction taskAction) {
            super(taskAction, ListenerAndText.forMemory(taskAction));
        }

        @Override
        protected void perform(TaskListener listener) throws Exception {
            listener.hyperlink("/localpath", "a link");
        }
    }

    private static class MyTaskAction extends TaskAction {
        void start() {
            workerThread = new MyTaskThread(this);
            workerThread.start();
        }

        @Override
        public String getIconFileName() {
            return "Iconfilename";
        }

        @Override
        public String getDisplayName() {
            return "My Task Thread";
        }

        @Override
        public String getUrlName() {
            return "xyz";
        }

        @Override
        protected Permission getPermission() {
            return Permission.READ;
        }

        @Override
        protected ACL getACL() {
            return ACL.lambda2((a, p) -> true);
        }
    }

    @Test
    void annotatedText() throws Exception {
        MyTaskAction action = new MyTaskAction();
        action.start();
        AnnotatedLargeText annotatedText = action.obtainLog();
        while (!annotatedText.isComplete()) {
            Thread.sleep(10);
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        final long length = annotatedText.writeLogTo(0, os);
        // Windows based systems will be 220, linux base 219
        assertTrue(length >= 219, "length should be longer or even 219");
        assertTrue(os.toString(StandardCharsets.UTF_8).startsWith("a linkCompleted"));
    }
}
