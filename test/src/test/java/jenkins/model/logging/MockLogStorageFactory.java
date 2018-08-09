/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc. and other Jenkins contributors
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
package jenkins.model.logging;

import hudson.model.Job;
import hudson.model.Run;

import javax.annotation.CheckForNull;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MockLogStorageFactory extends LogStorageFactory {

    @CheckForNull File baseDir;

    private transient Set<Job<?, ?>> jobCache = new HashSet<>();
    private transient Map<Loggable, File> logCache = new HashMap<>();

    private transient Set<Loggable> invocationList = new HashSet<>();

    public void setBaseDir(File baseDir) {
        this.baseDir = baseDir;
    }

    public void alterLogStorageFor(Job<?, ?> job) {
        jobCache.add(job);
    }

    @CheckForNull
    public File getLogFor(Loggable loggable) {
        return logCache.get(loggable);
    }

    @Override
    protected LogStorage getLogStorage(Loggable object) {
        invocationList.add(object);

        if (object instanceof Run) {
            Run<?, ?> run = (Run<?, ?>) object;
            Job<?, ?> parent = run.getParent();
            if (jobCache.contains(parent)) {
                File logDestination = new File(baseDir, parent.getFullName() + "/" + run.getNumber() + ".txt");
                logDestination.getParentFile().mkdirs();
                logCache.put(object, logDestination);
                return new MockLogStorage(object, logDestination);
            }
        }

        return null;
    }

    public void assertWasInvokedFor(Loggable object) throws AssertionError {
        if (!invocationList.contains(object)) {
            throw new AssertionError("Log storage factory was not invoked for " + object);
        }
    }
}
