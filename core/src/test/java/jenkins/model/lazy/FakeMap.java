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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class FakeMap extends AbstractLazyLoadRunMap<Build> {
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