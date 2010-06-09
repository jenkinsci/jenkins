/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.remoting;

import java.io.IOException;
import java.net.URL;

/**
 * {@link Callable} used to deliver a jar file to {@link RemoteClassLoader}.
 *
 * @author Kohsuke Kawaguchi
 */
final class PreloadJarTask implements DelegatingCallable<Boolean,IOException> {
    /**
     * Jar file to be preloaded.
     */
    private final URL[] jars;

    private transient ClassLoader target;

    PreloadJarTask(URL[] jars, ClassLoader target) {
        this.jars = jars;
        this.target = target;
    }

    public ClassLoader getClassLoader() {
        return target;
    }

    public Boolean call() throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (!(cl instanceof RemoteClassLoader))
            return false;

        RemoteClassLoader rcl = (RemoteClassLoader) cl;
        boolean r = false;
        for (URL jar : jars)
            r |= rcl.prefetch(jar);
        return r;
    }
}
