/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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

package jenkins.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Executor;
import hudson.model.ModelObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * A value class providing a consistent snapshot view of the state of an executor to avoid race conditions
 * during rendering of the executors list.
 */
@Restricted(NoExternalUse.class)
public class DisplayExecutor implements ModelObject, IDisplayExecutor {

    @NonNull
    private final String displayName;
    @NonNull
    private final String url;
    @NonNull
    private final Executor executor;

    public DisplayExecutor(@NonNull String displayName, @NonNull String url, @NonNull Executor executor) {
        this.displayName = displayName;
        this.url = url;
        this.executor = executor;
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    @Override
    @NonNull
    public String getUrl() {
        return url;
    }

    @Override
    @NonNull
    public Executor getExecutor() {
        return executor;
    }

    @Override
    public String toString() {
        return "DisplayExecutor{" + "displayName='" + displayName + '\'' +
                ", url='" + url + '\'' +
                ", executor=" + executor +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DisplayExecutor that = (DisplayExecutor) o;

        return executor.equals(that.executor);
    }

    @Override
    public int hashCode() {
        return executor.hashCode();
    }
}
