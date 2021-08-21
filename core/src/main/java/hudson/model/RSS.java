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

import hudson.FeedAdapter;
import hudson.util.RunList;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;

/**
 * RSS related code.
 *
 * @author Kohsuke Kawaguchi
 */
public final class RSS {

    /**
     * Sends the RSS feed to the client.
     *
     * @param title
     *      Title of the feed.
     * @param url
     *      URL of the model object that owns this feed. Relative to the context root.
     * @param entries
     *      Entries to be listed in the RSS feed.
     * @param adapter
     *      Controls how to render entries to RSS.
     */
    public static <E> void forwardToRss(String title, String url, Collection<? extends E> entries, FeedAdapter<E> adapter, StaplerRequest req, HttpServletResponse rsp) throws IOException, ServletException {
        req.setAttribute("adapter",adapter);
        req.setAttribute("title",title);
        req.setAttribute("url",url);
        req.setAttribute("entries",entries);

        String flavor = req.getParameter("flavor");
        if(flavor==null)    flavor="atom";
        flavor = flavor.replace('/', '_'); // Don't allow path to any jelly

        if (flavor.equals("atom")) {
            rsp.setContentType("application/atom+xml; charset=UTF-8");
        } else {
            rsp.setContentType("text/xml; charset=UTF-8");
        }

        req.getView(Jenkins.get(),"/hudson/"+flavor+".jelly").forward(req,rsp);
    }

    /**
     * Sends the RSS feed to the client using a default feed adapter.
     *
     * @param title
     *      Title of the feed.
     * @param url
     *      URL of the model object that owns this feed. Relative to the context root.
     * @param runList
     *      Entries to be listed in the RSS feed.
     * @since 2.215
     */
    public static void rss(StaplerRequest req, StaplerResponse rsp, String title, String url, RunList runList) throws IOException, ServletException {
        rss(req, rsp, title, url, runList, null);
    }

    /**
     * Sends the RSS feed to the client using a specific feed adapter.
     *
     * @param title
     *      Title of the feed.
     * @param url
     *      URL of the model object that owns this feed. Relative to the context root.
     * @param runList
     *      Entries to be listed in the RSS feed.
     * @param feedAdapter
     *      Controls how to render entries to RSS.
     * @since 2.215
     */
    public static void rss(StaplerRequest req, StaplerResponse rsp, String title, String url, RunList runList, FeedAdapter<Run> feedAdapter) throws IOException, ServletException {
        final FeedAdapter<Run> feedAdapter_ = feedAdapter == null ? Run.FEED_ADAPTER : feedAdapter;
        forwardToRss(title, url, runList, feedAdapter_, req, rsp);
    }
}
