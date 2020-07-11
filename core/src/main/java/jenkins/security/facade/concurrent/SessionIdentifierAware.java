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

package jenkins.security.facade.concurrent;

/**
 * Implemented by {@link org.acegisecurity.Authentication#getDetails()}
 * implementations that are capable of returning a session ID.
 *
 * <p>
 * This interface is used by {@link
 * org.acegisecurity.concurrent.SessionRegistryUtils} to extract the session
 * ID from an <code>Authentication</code> object. In turn,
 * <code>SessionRegistryUtils</code> is used by {@link
 * ConcurrentSessionControllerImpl}. If not using this latter implementation,
 * you do not need the <code>Authentication.getDetails()</code> object to
 * implement <code>SessionIdentifierAware</code>.
 * </p>
 *
 * Copied from acegi-security
 */
public interface SessionIdentifierAware {
    //~ Methods ========================================================================================================

    /**
     * Obtains the session ID.
     *
     * @return the session ID, or <code>null</code> if not known.
     */
    String getSessionId();
}
