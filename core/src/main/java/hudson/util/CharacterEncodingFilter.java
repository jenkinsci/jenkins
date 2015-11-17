/*
 *  The MIT License
 * 
 *  Copyright (c) 2010, Oracle Corporation, Seiji Sogabe
 * 
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 * 
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 * 
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package hudson.util;

import hudson.SystemProperties;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * Filter that sets the character encoding to be used in parsing the request
 * to avoid Non-ASCII characters garbled.
 *
 * @author Seiji Sogabe
 */
public class CharacterEncodingFilter implements Filter {

    /**
     * The default character encoding.
     */
    private static final String ENCODING = "UTF-8";

    private static final Boolean DISABLE_FILTER
            = SystemProperties.getBoolean(CharacterEncodingFilter.class.getName() + ".disableFilter");

    /**
     * The character encoding sets forcibly?
     */
    private static final Boolean FORCE_ENCODING
            = SystemProperties.getBoolean(CharacterEncodingFilter.class.getName() + ".forceEncoding");

    public void init(FilterConfig filterConfig) throws ServletException {
        LOGGER.log(Level.FINE,
                "CharacterEncodingFilter initialized. DISABLE_FILTER: {0} FORCE_ENCODING: {1}",
                new Object[]{DISABLE_FILTER, FORCE_ENCODING});
    }

    public void destroy() {
        LOGGER.fine("CharacterEncodingFilter destroyed.");
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!DISABLE_FILTER) {
            if (request instanceof HttpServletRequest) {
                HttpServletRequest req = (HttpServletRequest) request;
                if (shouldSetCharacterEncoding(req)) {
                    req.setCharacterEncoding(ENCODING);
                }
            }
        }

        chain.doFilter(request, response);
    }

    private boolean shouldSetCharacterEncoding(HttpServletRequest req) {
        String method = req.getMethod();
        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }

        // containers often implement RFCs incorrectly in that it doesn't interpret query parameter
        // decoding with UTF-8. This will ensure we get it right.
        // but doing this for config.xml submission could potentiall overwrite valid
        // "text/xml;charset=xxx"
        String contentType = req.getContentType();
        if (contentType != null) {
            boolean isXmlSubmission = contentType.startsWith("application/xml") || contentType.startsWith("text/xml");
            if (isXmlSubmission) {
                return false;
            }
        }

        if (FORCE_ENCODING || req.getCharacterEncoding() == null) {
            return true;
        }
        
        return false;
    }

    private static final Logger LOGGER = Logger.getLogger(CharacterEncodingFilter.class.getName());
}
