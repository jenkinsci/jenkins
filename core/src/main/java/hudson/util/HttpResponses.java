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
package hudson.util;

import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;

/**
 * Various {@link HttpResponse} implementations.
 *
 * <p>
 * This class extends from Stapler so that we can move implementations from here to Stapler periodically.
 *
 * @author Kohsuke Kawaguchi
 */
public class HttpResponses extends org.kohsuke.stapler.HttpResponses {
    public static HttpResponse staticResource(File f) throws IOException {
        return staticResource(f.toURI().toURL());
    }

    /**
     * Wraps a response as an exception for easier control flow.
     * @param hr a response to render
     * @return a corresponding exception (does not itself throw it)
     * @since TODO
     */
    public static HttpResponseException _throw(final HttpResponse hr) {
        return new HttpResponseException() {
            public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object o) throws IOException, ServletException {
                hr.generateResponse(req,rsp,o);
            }
        };
    }
}
