/*
 * The MIT License
 *
 * Copyright (c) 2012, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.model.lazy;

import org.apache.commons.io.FileUtils;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.IOException;

/**
 * Builder for creating a {@link FakeMap}
 *
 * @author Kohsuke Kawaguchi
 */
public class FakeMapBuilder implements TestRule {
    private File dir;

    protected File getDir() {
        return dir;
    }

    public FakeMapBuilder() {
    }

    public FakeMapBuilder add(int n, String id) throws IOException {
        verifyId(id);
        File build = new File(dir,id);
        build.mkdir();
        FileUtils.writeStringToFile(new File(build, "n"), Integer.toString(n));
        FileUtils.writeStringToFile(new File(build,"id"),id);
        return this;
    }

    /**
     * Adds a symlink from n to build id.
     *
     * (in test we should ideally create a symlink, but we fake the test
     * by actually making it a directory and staging the same data.)
     */
    public FakeMapBuilder addCache(int n, String id) throws IOException {
        return addBogusCache(n,n,id);
    }

    public FakeMapBuilder addBogusCache(int label, int actual, String id) throws IOException {
        verifyId(id);
        File build = new File(dir,Integer.toString(label));
        build.mkdir();
        FileUtils.writeStringToFile(new File(build, "n"), Integer.toString(actual));
        FileUtils.writeStringToFile(new File(build,"id"),id);
        return this;
    }

    public FakeMapBuilder addBoth(int n, String id) throws IOException {
        return add(n,id).addCache(n,id);
    }

    private void verifyId(String id) {
        try {
            Integer.parseInt(id);
            throw new IllegalMonitorStateException("ID cannot be a number");
        } catch (NumberFormatException e) {
            // OK
        }
    }

    /**
     * Adds a build record under the givn ID but make it unloadable,
     * which will cause a failure when a load is attempted on this build ID.
     */
    public FakeMapBuilder addUnloadable(String id) throws IOException {
        File build = new File(dir,id);
        build.mkdir();
        return this;
    }

    public FakeMapBuilder addUnloadableCache(int n) throws IOException {
        File build = new File(dir,String.valueOf(n));
        build.mkdir();
        return this;
    }

    public FakeMap make() {
        assert dir!=null;
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
