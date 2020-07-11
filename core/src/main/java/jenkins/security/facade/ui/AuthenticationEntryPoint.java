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
package jenkins.security.facade.ui;

import org.acegisecurity.AuthenticationException;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

/**
 * Used by {@link ExceptionTranslationFilter} to commence an authentication
 * scheme.
 *
 * Copied from acegi-security
 */
public interface AuthenticationEntryPoint {
    /**
     * Commences an authentication scheme.<P><code>SecurityEnforcementFilter</code> will populate the
     * <code>HttpSession</code> attribute named
     * <code>AuthenticationProcessingFilter.ACEGI_SECURITY_TARGET_URL_KEY</code> with the requested target URL before
     * calling this method.</p>
     *  <P>Implementations should modify the headers on the <code>ServletResponse</code> as necessary to
     * commence the authentication process.</p>
     *
     * @param request that resulted in an <code>AuthenticationException</code>
     * @param response so that the user agent can begin authentication
     * @param authException that caused the invocation
     *
     * @throws IOException DOCUMENT ME!
     * @throws ServletException DOCUMENT ME!
     */
    void commence(ServletRequest request, ServletResponse response, AuthenticationException authException)
            throws IOException, ServletException;
}
