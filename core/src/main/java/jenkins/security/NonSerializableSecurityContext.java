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

package jenkins.security;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpSession;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The same as {@link SecurityContextImpl} but doesn't serialize {@link Authentication}.
 *
 * <p>
 * {@link Authentication} often contains {@link UserDetails} implemented by a plugin,
 * but when it's persisted as a part of {@link HttpSession}, such instance will never
 * de-serialize correctly because the container isn't aware of additional classloading
 * in Jenkins.
 *
 * <p>
 * Jenkins doesn't work with a clustering anyway, and so it's better to just not persist
 * Authentication at all.
 *
 * See <a href="http://jenkins.361315.n4.nabble.com/ActiveDirectory-Plugin-ClassNotFoundException-while-loading-persisted-sessions-tp376451.html">the problem report</a>.
 *
 * @author Kohsuke Kawaguchi
 * @see hudson.security.HttpSessionContextIntegrationFilter2
 * @since 1.509
 */
@SuppressFBWarnings(
        value = {"SE_NO_SERIALVERSIONID", "SE_TRANSIENT_FIELD_NOT_RESTORED"},
        justification = "It is not intended to be serialized. Default values will be used in case of deserialization")
@Restricted(NoExternalUse.class)
public class NonSerializableSecurityContext implements SecurityContext {
    private transient Authentication authentication;

    public NonSerializableSecurityContext() {
    }

    public NonSerializableSecurityContext(Authentication authentication) {
        this.authentication = authentication;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SecurityContext) {
            SecurityContext test = (SecurityContext) obj;

            if (this.getAuthentication() == null && test.getAuthentication() == null) {
                return true;
            }

            if (this.getAuthentication() != null && test.getAuthentication() != null
                && this.getAuthentication().equals(test.getAuthentication())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Authentication getAuthentication() {
        return authentication;
    }

    @Override
    public int hashCode() {
        if (this.authentication == null) {
            return -1;
        } else {
            return this.authentication.hashCode();
        }
    }

    @Override
    public void setAuthentication(Authentication authentication) {
        this.authentication = authentication;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());

        if (this.authentication == null) {
            sb.append(": Null authentication");
        } else {
            sb.append(": Authentication: ").append(this.authentication);
        }

        return sb.toString();
    }
}
