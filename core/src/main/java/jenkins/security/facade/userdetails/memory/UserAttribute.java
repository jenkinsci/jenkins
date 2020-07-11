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

package jenkins.security.facade.userdetails.memory;

import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;


/**
 * Used by {@link InMemoryDaoImpl} to temporarily store the attributes associated with a user.
 *
 * Copied from acegi-security
 * PATCH: only used in SecurityRealm.groovy (due to AnonymousProcessingFilter)
 */
public class UserAttribute {
    //~ Instance fields ================================================================================================

    private List authorities = new Vector();
    private String password;
    private boolean enabled = true;

    //~ Constructors ===================================================================================================

    public UserAttribute() {
        super();
    }

    //~ Methods ========================================================================================================

    public void addAuthority(GrantedAuthority newAuthority) {
        this.authorities.add(newAuthority);
    }

    public GrantedAuthority[] getAuthorities() {
        GrantedAuthority[] toReturn = {new GrantedAuthorityImpl("demo")};

        return (GrantedAuthority[]) this.authorities.toArray(toReturn);
    }

    /**
     * Set all authorities for this user.
     * 
     * @param authorities {@link List} &lt;{@link GrantedAuthority}>
     * @since 1.1
     */
    public void setAuthorities(List authorities) {
        this.authorities = authorities;
    }

    /**
     * Set all authorities for this user from String values.
     * It will create the necessary {@link GrantedAuthority} objects.
     * 
     * @param authoritiesAsString {@link List} &lt;{@link String}>
     * @since 1.1
     */
    public void setAuthoritiesAsString(List authoritiesAsString) {
        setAuthorities(new ArrayList(authoritiesAsString.size()));
        Iterator it = authoritiesAsString.iterator();
        while (it.hasNext()) {
            GrantedAuthority grantedAuthority = new GrantedAuthorityImpl((String) it.next());
            addAuthority(grantedAuthority);
        }
    }

    public String getPassword() {
        return password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isValid() {
        if ((this.password != null) && (authorities.size() > 0)) {
            return true;
        } else {
            return false;
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
