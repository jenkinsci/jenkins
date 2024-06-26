/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, id:digerata
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

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import org.kohsuke.stapler.CompatibleFilter;

/**
 * This filter allows you to modify headers set by the container or other servlets
 * that are out of your control.  The particular headers you wish to change are configured
 * in web.xml.
 * <p>
 * One particular header you you may wish to deal with is "Cache-Control: no-cache"
 * This is a problem with Tomcat when security is used.  Continue reading for further details.
 * <p>
 * If a web app has a &lt;security-constraint&gt; in its web.xml, Tomcat will
 * add a Cache-Control header to every file it serves from that location. This
 * header will prevent browsers from caching the file locally and this drastically slows
 * down Hudson page load times.
 * <p>
 * To enable this filter, edit the web.xml file to include:
 *
 * <pre>
 * &lt;filter&gt;
 *         &lt;filter-name&gt;change-headers-filter&lt;/filter-name&gt;
 *         &lt;filter-class&gt;hudson.ResponseHeaderFilter&lt;/filter-class&gt;
 *         &lt;init-param&gt;
 *             &lt;param-name&gt;Pragma&lt;/param-name&gt;
 *             &lt;param-value&gt;public&lt;/param-value&gt;
 *         &lt;/init-param&gt;
 *         &lt;init-param&gt;
 *             &lt;param-name&gt;Cache-Control&lt;/param-name&gt;
 *             &lt;param-value&gt;max-age=86400, public&lt;/param-value&gt;
 *         &lt;/init-param&gt;
 * &lt;/filter&gt;
 *
 * And down below that:
 *
 * &lt;filter-mapping&gt;
 *         &lt;filter-name&gt;Headers&lt;/filter-name&gt;
 *         &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 * &lt;/filter-mapping&gt;
 * </pre>
 *
 * <p>
 * In the case of the tomcat cache problem, it is important that the url-pattern for
 * the filter matches the url-pattern set for the security-constraint.
 *
 * @author Mike Wille
 */
public class ResponseHeaderFilter implements CompatibleFilter {
    private FilterConfig config;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        config = filterConfig;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException,
            ServletException {
        HttpServletResponse httpResp = (HttpServletResponse) resp;

        Enumeration e = config.getInitParameterNames();

        // for each configuration element...
        while (e.hasMoreElements()) {
            String headerName = (String) e.nextElement();
            String headerValue = config.getInitParameter(headerName);
            // set the header with the given name and value
            httpResp.setHeader(headerName, headerValue);
        }
        chain.doFilter(req, resp);
    }

    @Override
    public void destroy() {
    }
}
