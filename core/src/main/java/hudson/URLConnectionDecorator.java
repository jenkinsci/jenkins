/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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
package hudson;

import jenkins.model.Jenkins;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

/**
 * Decorates the connections that Jenkins open to access external resources.
 *
 * @see ProxyConfiguration#open(URL)
 * @author Kohsuke Kawaguchi
 * @since 1.426
 */
public abstract class URLConnectionDecorator implements ExtensionPoint {
    /**
     * Called before it gets connected. Can be used to tweak parameters.
     */
    public abstract void decorate(URLConnection con) throws IOException;

    /**
     * Returns all the registered {@link URLConnectionDecorator}s.
     */
    public static ExtensionList<URLConnectionDecorator> all() {
        return ExtensionList.lookup(URLConnectionDecorator.class);
    }
}
