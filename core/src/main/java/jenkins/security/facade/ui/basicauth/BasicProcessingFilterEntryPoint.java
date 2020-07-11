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

package jenkins.security.facade.ui.basicauth;

import jenkins.security.facade.ui.AuthenticationEntryPoint;
import org.acegisecurity.AuthenticationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


/**
 * Used by the <code>SecurityEnforcementFilter</code> to commence authentication via the {@link
 * BasicProcessingFilter}.<P>Once a user agent is authenticated using BASIC authentication, logout requires that
 * the browser be closed or an unauthorized (401) header be sent. The simplest way of achieving the latter is to call
 * the {@link #commence(ServletRequest, ServletResponse, AuthenticationException)} method below. This will indicate to
 * the browser its credentials are no longer authorized, causing it to prompt the user to login again.</p>
 *
 * Copied from acegi-security
 * PATCH: only used in SecurityFilters.groovy
 */
public class BasicProcessingFilterEntryPoint implements AuthenticationEntryPoint, InitializingBean {
    //~ Instance fields ================================================================================================

    private String realmName;

    //~ Methods ========================================================================================================

	public void afterPropertiesSet() throws Exception {
		Assert.hasText(realmName, "realmName must be specified");
    }

    public void commence(ServletRequest request, ServletResponse response, AuthenticationException authException)
        throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.addHeader("WWW-Authenticate", "Basic realm=\"" + realmName + "\"");
        httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, authException.getMessage());
    }

    public String getRealmName() {
        return realmName;
    }

    public void setRealmName(String realmName) {
        this.realmName = realmName;
    }

}
