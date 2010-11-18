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
 * {@link Notifier} is a kind of {@link Publisher} that sends out the outcome of the builds to
 * other systems and humans. This marking ensures that notifiers are run after the build result
 * is set to its final value by other {@link Recorder}s.  To run even after the build is marked
 * as complete, override {@link #needsToRunAfterFinalized} to return true.
 *
 * <p>
 * To register a custom {@link Publisher} from a plugin,
 * put {@link Extension} on your descriptor.
 *
 *
 * @author Kohsuke Kawaguchi
 * @since 1.286
 * @see Recorder
 */
public abstract class Notifier extends Publisher implements ExtensionPoint {
    @SuppressWarnings("deprecation") // super only @Deprecated to discourage other subclasses
    protected Notifier() {}
    public BuildStepDescriptor getDescriptor() {
        return (BuildStepDescriptor)super.getDescriptor();
    }
}
