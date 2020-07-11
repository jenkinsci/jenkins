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

package jenkins.security.facade.ui.logout;

import org.acegisecurity.Authentication;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * Indicates a class that is able to participate in logout handling.
 *
 * <p>
 * Called by {@link LogoutFilter}.
 *
 * Copied from acegi-security
 */
public interface LogoutHandler {
    //~ Methods ========================================================================================================

    /**
     * Causes a logout to be completed. The method must complete successfully.
     *
     * @param request the HTTP request
     * @param response the HTTP resonse
     * @param authentication the current principal details
     */
    void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication);
}
