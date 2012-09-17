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
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;

/**
 * RSS related code.
 *
 * @author Kohsuke Kawaguchi
 */
public final class RSS {

    /**
     * Parses trackback ping.
     */
    public static void doTrackback( Object it, StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {

        String url = req.getParameter("url");

        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("application/xml; charset=UTF-8");
        PrintWriter pw = rsp.getWriter();
        pw.println("<response>");
        pw.println("<error>"+(url!=null?0:1)+"</error>");
        if(url==null) {
            pw.println("<message>url must be specified</message>");
        }
        pw.println("</response>");
        pw.close();
    }

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
        req.setAttribute("rootURL", Jenkins.getInstance().getRootUrl());

        String flavor = req.getParameter("flavor");
        if(flavor==null)    flavor="atom";
        flavor = flavor.replace('/', '_'); // Don't allow path to any jelly

        req.getView(Jenkins.getInstance(),"/hudson/"+flavor+".jelly").forward(req,rsp);
    }
}
