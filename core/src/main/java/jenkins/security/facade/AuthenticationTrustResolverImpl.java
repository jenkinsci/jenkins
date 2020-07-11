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

package jenkins.security.facade;

import jenkins.security.facade.providers.anonymous.AnonymousAuthenticationToken;
import jenkins.security.facade.providers.rememberme.RememberMeAuthenticationToken;
import org.acegisecurity.Authentication;


/**
 * Basic implementation of {@link AuthenticationTrustResolver}.<p>Makes trust decisions based on whether the passed
 * <code>Authentication</code> is an instance of a defined class.</p>
 *  <p>If {@link #anonymousClass} or {@link #rememberMeClass} is <code>null</code>, the corresponding method will
 * always return <code>false</code>.</p>
 *
 * Copied from acegi-security
 */
public class AuthenticationTrustResolverImpl implements AuthenticationTrustResolver {
    //~ Instance fields ================================================================================================

    private Class anonymousClass = AnonymousAuthenticationToken.class;
    private Class rememberMeClass = RememberMeAuthenticationToken.class;

    //~ Methods ========================================================================================================

    public Class getAnonymousClass() {
        return anonymousClass;
    }

    public Class getRememberMeClass() {
        return rememberMeClass;
    }

    public boolean isAnonymous(Authentication authentication) {
        if ((anonymousClass == null) || (authentication == null)) {
            return false;
        }

        return anonymousClass.isAssignableFrom(authentication.getClass());
    }

    public boolean isRememberMe(Authentication authentication) {
        if ((rememberMeClass == null) || (authentication == null)) {
            return false;
        }

        return rememberMeClass.isAssignableFrom(authentication.getClass());
    }

    public void setAnonymousClass(Class anonymousClass) {
        this.anonymousClass = anonymousClass;
    }

    public void setRememberMeClass(Class rememberMeClass) {
        this.rememberMeClass = rememberMeClass;
    }
}
