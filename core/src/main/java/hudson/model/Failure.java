/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.model;

import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Represents an error induced by user, encountered during HTTP request processing.
 *
 * <p>
 * The error page is rendered into HTML, but without a stack trace. So only use
 * this exception when the error condition is anticipated by the program, and where
 * we nor users don't need to see the stack trace to figure out the root cause. 
 *
 * @author Kohsuke Kawaguchi
 * @since 1.321
 */
public class Failure extends RuntimeException implements HttpResponse {
    private final boolean pre;

    public Failure(String message) {
        this(message,false);
    }

    public Failure(String message, boolean pre) {
        super(message);
        this.pre = pre;
    }

    public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node, @Nullable Throwable throwable) throws IOException, ServletException {
        if (throwable != null) {
            req.setAttribute("exception", throwable);
        }
        generateResponse(req, rsp, node);
    }

    public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
        req.setAttribute("message",getMessage());
        if(pre)
            req.setAttribute("pre",true);
        if (node instanceof AbstractItem) // Maintain ancestors
            rsp.forward(Jenkins.getInstance(), ((AbstractItem)node).getUrl() + "error", req);
        else
            rsp.forward(node instanceof AbstractModelObject ? node : Jenkins.getInstance() ,"error", req);
    }
}
