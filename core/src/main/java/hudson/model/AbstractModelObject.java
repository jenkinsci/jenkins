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

package hudson.model;

import hudson.search.Search;
import hudson.search.SearchFactory;
import hudson.search.SearchIndex;
import hudson.search.SearchIndexBuilder;
import hudson.search.SearchableModelObject;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * {@link ModelObject} with some convenience methods.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractModelObject implements SearchableModelObject {
    /**
     * Displays the error in a page.
     */
    protected final void sendError(Exception e, StaplerRequest2 req, StaplerResponse2 rsp) throws ServletException, IOException {
        req.setAttribute("exception", e);
        sendError(e.getMessage(), req, rsp);
    }

    /**
     * @deprecated use {@link #sendError(Exception, StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    protected final void sendError(Exception e, StaplerRequest req, StaplerResponse rsp) throws javax.servlet.ServletException, IOException {
        try {
            sendError(e, StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
        } catch (ServletException ex) {
            throw ServletExceptionWrapper.fromJakartaServletException(ex);
        }
    }

    protected final void sendError(Exception e) throws ServletException, IOException {
        sendError(e, Stapler.getCurrentRequest2(), Stapler.getCurrentResponse2());
    }

    protected final void sendError(String message, StaplerRequest2 req, StaplerResponse2 rsp) throws ServletException, IOException {
        req.setAttribute("message", message);
        rsp.forward(this, "error", req);
    }

    /**
     * @deprecated use {@link #sendError(String, StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    protected final void sendError(String message, StaplerRequest req, StaplerResponse rsp) throws javax.servlet.ServletException, IOException {
        try {
            sendError(message, StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    /**
     * @param pre
     *      If true, the message is put in a PRE tag.
     */
    protected final void sendError(String message, StaplerRequest2 req, StaplerResponse2 rsp, boolean pre) throws ServletException, IOException {
        req.setAttribute("message", message);
        if (pre)
            req.setAttribute("pre", true);
        rsp.forward(this, "error", req);
    }

    /**
     * @deprecated use {@link #sendError(String, StaplerRequest2, StaplerResponse2, boolean)}
     */
    @Deprecated
    protected final void sendError(String message, StaplerRequest req, StaplerResponse rsp, boolean pre) throws javax.servlet.ServletException, IOException {
        try {
            sendError(message, StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp), pre);
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    protected final void sendError(String message) throws ServletException, IOException {
        sendError(message, Stapler.getCurrentRequest2(), Stapler.getCurrentResponse2());
    }

    /**
     * Convenience method to verify that the current request is a POST request.
     *
     * @deprecated
     *      Use {@link RequirePOST} on your method.
     */
    @Deprecated
    protected final void requirePOST() throws ServletException {
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req == null)  return; // invoked outside the context of servlet
        String method = req.getMethod();
        if (!method.equalsIgnoreCase("POST"))
            throw new ServletException("Must be POST, Can't be " + method);
    }

    /**
     * Default implementation that returns empty index.
     */
    protected SearchIndexBuilder makeSearchIndex() {
        return new SearchIndexBuilder().addAllAnnotations(this);
    }

    @Override
    public final SearchIndex getSearchIndex() {
        return makeSearchIndex().make();
    }

    @Override
    public Search getSearch() {
        for (SearchFactory sf : SearchFactory.all()) {
            Search s = sf.createFor(this);
            if (s != null)
                return s;
        }
        return new Search();
    }

    /**
     * Default implementation that returns the display name.
     */
    @Override
    public String getSearchName() {
        return getDisplayName();
    }
}
