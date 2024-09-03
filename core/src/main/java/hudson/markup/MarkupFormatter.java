/*
 * The MIT License
 *
 * Copyright (c) 2010, CloudBees, Inc.
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

package hudson.markup;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.verb.GET;
import org.kohsuke.stapler.verb.POST;

/**
 * Generalization of a function that takes text with some markup and converts that to HTML.
 * Such markup is often associated with Wiki.
 *
 * <p>
 * Use of markup, as opposed to using raw HTML, ensures certain degree of security.
 *
 * <p>
 * This is an extension point in Hudson, allowing plugins to implement different markup formatters.
 *
 * <p>
 * Implement the following methods to enable and control CodeMirror syntax highlighting
 * public String getCodeMirrorMode() // return null to disable CodeMirror dynamically
 * public String getCodeMirrorConfig()
 *
 * <h2>Views</h2>
 * <p>
 * This extension point must have a valid {@code config.jelly} that feeds the constructor.
 *
 * TODO: allow {@link MarkupFormatter} to control the UI that the user uses to edit.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.391
 * @see jenkins.model.Jenkins#getMarkupFormatter()
 */
public abstract class MarkupFormatter extends AbstractDescribableImpl<MarkupFormatter> implements ExtensionPoint {
    private static final Logger LOGGER = Logger.getLogger(MarkupFormatter.class.getName());

    private static /* non-final */ boolean PREVIEWS_ALLOW_GET = SystemProperties.getBoolean(MarkupFormatter.class.getName() + ".previewsAllowGET");
    private static /* non-final */ boolean PREVIEWS_SET_CSP = SystemProperties.getBoolean(MarkupFormatter.class.getName() + ".previewsSetCSP", true);

    /**
     * Given the text, converts that to HTML according to whatever markup rules implicit in the implementation class.
     *
     * <p>
     * Multiple threads can call this method concurrently with different inputs.
     *
     * @param output
     *      Formatted HTML should be sent to this output.
     */
    public abstract void translate(@CheckForNull String markup, @NonNull Writer output) throws IOException;

    public final @NonNull String translate(@CheckForNull String markup) throws IOException {
        StringWriter w = new StringWriter();
        translate(markup, w);
        return w.toString();
    }

    /**
     * Gets the URL of the help file. This help will shown next to the text area of the description,
     * and is ideal of describing what the allowed syntax is.
     *
     * By default, we look for colocated help-syntax.html.
     *
     * @since 1.398
     * @return null
     *      If there's no help file.
     */
    public String getHelpUrl() {
        return getDescriptor().getHelpFile("syntax");
    }

    @Override
    public MarkupFormatterDescriptor getDescriptor() {
        return (MarkupFormatterDescriptor) super.getDescriptor();
    }

    /**
     * Generate HTML for preview, using markup formatter.
     * Can be called from other views.
     */
    @POST
    public HttpResponse doPreviewDescription(@QueryParameter String text) throws IOException {
        StringWriter w = new StringWriter();
        translate(text, w);
        Map<String, String> extraHeaders = Collections.emptyMap();
        if (PREVIEWS_SET_CSP) {
            extraHeaders = Stream.of("Content-Security-Policy", "X-WebKit-CSP", "X-Content-Security-Policy").collect(Collectors.toMap(Function.identity(), v -> "default-src 'none';"));
        }
        return html(200, w.toString(), extraHeaders);
    }

    /**
     * Handle GET requests sent to the /previewDescription URL.
     * @return an HTTP response informing users that requests need to be sent via POST
     */
    @GET
    @WebMethod(name = "previewDescription")
    @Restricted(NoExternalUse.class)
    public HttpResponse previewsNowNeedPostForSecurity2153(@QueryParameter String text, StaplerRequest2 req) throws IOException {
        LOGGER.log(Level.FINE, "Received a GET request at " + req.getRequestURL());
        if (PREVIEWS_ALLOW_GET) {
            return doPreviewDescription(text);
        }
        return html(405, "This endpoint now requires that POST requests are sent. Update the component implementing this preview feature.", Collections.emptyMap());
    }

    /**
     * Returns a basic HTML response with the provided status and additional headers set
     * @param status the HTTP status code
     * @param html the HTML response body
     * @param headers the additional headers to set
     * @return the response
     */
    private static HttpResponse html(int status, @NonNull String html, @NonNull Map<String, String> headers) {
        // TODO Move to Stapler's HttpResponses, (also add a corresponding 'text' method)
        return new HttpResponse() {
            @Override
            public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
                rsp.setContentType("text/html;charset=UTF-8");
                rsp.setStatus(status);
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    rsp.setHeader(header.getKey(), header.getValue());
                }
                PrintWriter pw = rsp.getWriter();
                pw.print(html);
                pw.flush();
            }
        };
    }
}
