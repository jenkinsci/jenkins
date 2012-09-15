import org.apache.commons.io.FileUtils;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class FakeMapBuilder implements TestRule {
    private File dir;

    public FakeMapBuilder() {
    }

    public FakeMapBuilder add(int n, String id) throws IOException {
        try {
            Integer.parseInt(id);
            throw new IllegalMonitorStateException("ID cannot be a number");
        } catch (NumberFormatException e) {
            // OK
        }

        File build = new File(dir,id);
        build.mkdir();
        FileUtils.write(new File(build,"n"),Integer.toString(n));
        return this;
    }

    public FakeMap make() {
        return new FakeMap(dir);
    }

    public Statement apply(final Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                dir = File.createTempFile("lazyload","test");
                dir.delete();
                dir.mkdirs();
                try {
                    base.evaluate();
                } finally {
                    FileUtils.deleteDirectory(dir);
                }
            }
        };
    }
}
