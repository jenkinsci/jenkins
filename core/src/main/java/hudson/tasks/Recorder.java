/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.tasks;

import hudson.Extension;
import hudson.ExtensionPoint;

/**
 * {@link BuildStep}s that run after the build is completed.
 *
 * <p>
 * {@link Recorder} is a kind of {@link Publisher} that collects statistics from the build,
 * and can mark builds as unstable/failure. This marking ensures that builds are marked accordingly
 * before notifications are sent via {@link Notifier}s. Otherwise, if the build is marked failed
 * after some notifications are sent, inconsistency ensues.
 *
 * <p>
 * To register a custom {@link Publisher} from a plugin,
 * put {@link Extension} on your descriptor.
 *
 *
 * @author Kohsuke Kawaguchi
 * @since 1.286
 * @see Notifier
 */
public abstract class Recorder extends Publisher implements ExtensionPoint {
    @SuppressWarnings("deprecation") // super only @Deprecated to discourage other subclasses
    protected Recorder() {}
    public BuildStepDescriptor getDescriptor() {
        return (BuildStepDescriptor)super.getDescriptor();
    }
}
