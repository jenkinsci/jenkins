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

import javax.servlet.http.HttpServletRequest;


/**
 * Provides a {@link org.acegisecurity.Authentication#getDetails()} object for
 * a given web request.
 *
 * Copied from acegi-security
 */
public interface AuthenticationDetailsSource {
    //~ Methods ========================================================================================================

    /**
     * Called by a class when it wishes a new authentication details instance to be created.
     *
     * @param request the request object, which may be used by the authentication details object
     *
     * @return a fully-configured authentication details instance
     */
    Object buildDetails(HttpServletRequest request);
}
