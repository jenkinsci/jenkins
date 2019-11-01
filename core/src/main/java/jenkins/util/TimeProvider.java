/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
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
package jenkins.util;

import hudson.Extension;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;

public interface TimeProvider extends ExtensionPoint {
    long getCurrentTimeMillis();

    static @Nonnull TimeProvider get() {
        return Jenkins.get().getExtensionList(TimeProvider.class).get(0);
    }

    // extension with default ordinal will take precedence
    int DEFAULT_TIME_SERVICE_ORDINAL = -10000;

    @Extension(ordinal = DEFAULT_TIME_SERVICE_ORDINAL)
    class DefaultTimeProvider implements TimeProvider {
        @Override
        public long getCurrentTimeMillis() {
            return System.currentTimeMillis();
        }
    }
}
