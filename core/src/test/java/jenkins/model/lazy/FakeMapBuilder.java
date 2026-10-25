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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Builder for creating a {@link FakeMap}
 *
 * @author Kohsuke Kawaguchi
 */
public class FakeMapBuilder {
    private File dir;

    public FakeMapBuilder() {
        try {
            dir = File.createTempFile("lazyload", "test");
            dir.delete();
            dir.mkdirs();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected File getDir() {
        return dir;
    }

    public FakeMapBuilder add(int n) throws IOException {
        File build = new File(dir, Integer.toString(n));
        Files.createDirectory(build.toPath());
        Files.writeString(build.toPath().resolve("n"), Integer.toString(n), StandardCharsets.US_ASCII);
        return this;
    }

    /**
     * Adds a build record under the given ID but make it unloadable,
     * which will cause a failure when a load is attempted on this build ID.
     */
    public FakeMapBuilder addUnloadable(int n) {
        File build = new File(dir, Integer.toString(n));
        build.mkdir();
        return this;
    }

    public FakeMap make() {
        assertNotNull(dir);
        return new FakeMap(dir);
    }
}
