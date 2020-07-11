/* Copyright 2004, 2005, 2006 Acegi Technology Pty Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jenkins.security.facade.util;

import jenkins.security.facade.ui.savedrequest.SavedRequest;

/**
 * Provides static methods for composing URLs.<p>Placed into a separate class for visibility, so that changes to
 * URL formatting conventions will affect all users.</p>
 *
 * Copied from acegi-security
 * PATCH: only used in LegacySecurityRealm context
 */
public final class UrlUtils {
    //~ Constructors ===================================================================================================

    private UrlUtils() {
    }

    //~ Methods ========================================================================================================

    /**
     * Obtains the full URL the client used to make the request.<p>Note that the server port will not be shown
     * if it is the default server port for HTTP or HTTPS (ie 80 and 443 respectively).</p>
     *
     * @param scheme DOCUMENT ME!
     * @param serverName DOCUMENT ME!
     * @param serverPort DOCUMENT ME!
     * @param contextPath DOCUMENT ME!
     * @param requestUrl DOCUMENT ME!
     * @param servletPath DOCUMENT ME!
     * @param requestURI DOCUMENT ME!
     * @param pathInfo DOCUMENT ME!
     * @param queryString DOCUMENT ME!
     *
     * @return the full URL
     */
    private static String buildFullRequestUrl(String scheme, String serverName, int serverPort, String contextPath,
        String requestUrl, String servletPath, String requestURI, String pathInfo, String queryString) {

        boolean includePort = true;

        if ("http".equals(scheme.toLowerCase()) && (serverPort == 80)) {
            includePort = false;
        }

        if ("https".equals(scheme.toLowerCase()) && (serverPort == 443)) {
            includePort = false;
        }

        return scheme + "://" + serverName + ((includePort) ? (":" + serverPort) : "") + contextPath
        + buildRequestUrl(servletPath, requestURI, contextPath, pathInfo, queryString);
    }

    /**
     * Obtains the web application-specific fragment of the URL.
     *
     * @param servletPath DOCUMENT ME!
     * @param requestURI DOCUMENT ME!
     * @param contextPath DOCUMENT ME!
     * @param pathInfo DOCUMENT ME!
     * @param queryString DOCUMENT ME!
     *
     * @return the URL, excluding any server name, context path or servlet path
     */
    private static String buildRequestUrl(String servletPath, String requestURI, String contextPath, String pathInfo,
        String queryString) {

        String uri = servletPath;

        if (uri == null) {
            uri = requestURI;
            uri = uri.substring(contextPath.length());
        }

        return uri + ((pathInfo == null) ? "" : pathInfo) + ((queryString == null) ? "" : ("?" + queryString));
    }

    //PATCH: not used
//    public static String getFullRequestUrl(FilterInvocation fi) {
//        HttpServletRequest r = fi.getHttpRequest();
//
//        return buildFullRequestUrl(r.getScheme(), r.getServerName(), r.getServerPort(), r.getContextPath(),
//            r.getRequestURL().toString(), r.getServletPath(), r.getRequestURI(), r.getPathInfo(), r.getQueryString());
//    }

    public static String getFullRequestUrl(SavedRequest sr) {
        return buildFullRequestUrl(sr.getScheme(), sr.getServerName(), sr.getServerPort(), sr.getContextPath(),
            sr.getRequestURL(), sr.getServletPath(), sr.getRequestURI(), sr.getPathInfo(), sr.getQueryString());
    }
    
    //PATCH: not used
//    public static String getRequestUrl(FilterInvocation fi) {
//        HttpServletRequest r = fi.getHttpRequest();
//
//        return buildRequestUrl(r.getServletPath(), r.getRequestURI(), r.getContextPath(), r.getPathInfo(),
//            r.getQueryString());
//    }

    public static String getRequestUrl(SavedRequest sr) {
        return buildRequestUrl(sr.getServletPath(), sr.getRequestURI(), sr.getContextPath(), sr.getPathInfo(),
            sr.getQueryString());
    }
}
