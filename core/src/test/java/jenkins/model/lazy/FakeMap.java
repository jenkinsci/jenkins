package jenkins.model.lazy;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class FakeMap extends Attempt2<Build> {
    public FakeMap(File dir) {
        super(dir);
    }

    @Override
    protected int getNumberOf(Build build) {
        return build.n;
    }

    @Override
    protected String getIdOf(Build build) {
        return build.id;
    }

    @Override
    protected FilenameFilter createDirectoryFilter() {
        return new FilenameFilter() {
            public boolean accept(File dir, String name) {
                try {
                    Integer.parseInt(name);
                    return false;
                } catch (NumberFormatException e) {
                    return true;
                }
            }
        };
    }

    @Override
    protected Build retrieve(File dir) throws IOException {
        String n = FileUtils.readFileToString(new File(dir, "n")).trim();
        String id = FileUtils.readFileToString(new File(dir, "id")).trim();
        return new Build(Integer.parseInt(n),id);
    }
}

class Build {
    final int n;
    final String id;

    Build(int n, String id) {
        this.n = n;
        this.id = id;
    }

    public void asserts(int n, String id) {
        assert this.n==n;
        assert this.id.equals(id);
    }
}