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

package jenkins.security.facade.event.authentication;

import org.acegisecurity.Authentication;
import org.springframework.context.ApplicationEvent;


/**
 * Represents an application authentication event.<P>The <code>ApplicationEvent</code>'s <code>source</code> will
 * be the <code>Authentication</code> object.</p>
 *
 * Copied from acegi-security
 * PATCH: nobody uses the child event
 */
public abstract class AbstractAuthenticationEvent extends ApplicationEvent {
    //~ Constructors ===================================================================================================

    public AbstractAuthenticationEvent(Authentication authentication) {
        super(authentication);
    }

    //~ Methods ========================================================================================================

    /**
     * Getters for the <code>Authentication</code> request that caused the event. Also available from
     * <code>super.getSource()</code>.
     *
     * @return the authentication request
     */
    public Authentication getAuthentication() {
        return (Authentication) super.getSource();
    }
}
