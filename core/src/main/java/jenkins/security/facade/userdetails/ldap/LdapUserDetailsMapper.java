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

package jenkins.security.facade.userdetails.ldap;

import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.acegisecurity.ldap.LdapEntryMapper;
import org.acegisecurity.userdetails.ldap.LdapUserDetailsImpl;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;


/**
 * The entry mapper used by the authenticators to create an ldap user object.
 *
 * Copied from acegi-security
 */
public class LdapUserDetailsMapper implements LdapEntryMapper {
    //~ Instance fields ================================================================================================

    private final Log logger = LogFactory.getLog(LdapUserDetailsMapper.class);
    private String passwordAttributeName = "userPassword";
    private String rolePrefix = "ROLE_";
    private String[] roleAttributes = null;
    private boolean convertToUpperCase = true;

    //~ Methods ========================================================================================================

    public Object mapAttributes(String dn, Attributes attributes)
        throws NamingException {
        LdapUserDetailsImpl.Essence essence = new LdapUserDetailsImpl.Essence();

        essence.setDn(dn);
        essence.setAttributes(attributes);

        Attribute passwordAttribute = attributes.get(passwordAttributeName);

        if (passwordAttribute != null) {
            essence.setPassword(mapPassword(passwordAttribute));
        }

        // Map the roles
        for (int i = 0; (roleAttributes != null) && (i < roleAttributes.length); i++) {
            Attribute roleAttribute = attributes.get(roleAttributes[i]);

            if (roleAttribute == null) {
                logger.debug("Couldn't read role attribute '" + roleAttributes[i] + "' for user " + dn);
                continue;
            }

            NamingEnumeration attributeRoles = roleAttribute.getAll();

            while (attributeRoles.hasMore()) {
                GrantedAuthority authority = createAuthority(attributeRoles.next());

                if (authority != null) {
                    essence.addAuthority(authority);
                } else {
                    logger.debug("Failed to create an authority value from attribute with Id: "
                            + roleAttribute.getID());
                }
            }
        }

        return essence;
    }

    /**
     * Extension point to allow customized creation of the user's password from
     * the attribute stored in the directory.
     *
     * @param passwordAttribute the attribute instance containing the password
     * @return a String representation of the password.
     */
    protected String mapPassword(Attribute passwordAttribute) throws NamingException {
        Object retrievedPassword = passwordAttribute.get();

        if (!(retrievedPassword instanceof String)) {
            // Assume it's binary
            retrievedPassword = new String((byte[]) retrievedPassword);
        }

        return (String) retrievedPassword;

    }

    /**
     * Creates a GrantedAuthority from a role attribute. Override to customize
     * authority object creation.
     * <p>
     * The default implementation converts string attributes to roles, making use of the <tt>rolePrefix</tt>
     * and <tt>convertToUpperCase</tt> properties. Non-String attributes are ignored.
     * </p>
     *
     * @param role the attribute returned from
     * @return the authority to be added to the list of authorities for the user, or null
     * if this attribute should be ignored.
     */
    protected GrantedAuthority createAuthority(Object role) {
        if (role instanceof String) {
            if (convertToUpperCase) {
                role = ((String) role).toUpperCase();
            }
            return new GrantedAuthorityImpl(rolePrefix + role);
        }
        return null;
    }

    /**
     * Determines whether role field values will be converted to upper case when loaded.
     * The default is true.
     *
     * @param convertToUpperCase true if the roles should be converted to upper case.
     */
    public void setConvertToUpperCase(boolean convertToUpperCase) {
        this.convertToUpperCase = convertToUpperCase;
    }

    /**
     * The name of the attribute which contains the user's password.
     * Defaults to "userPassword".
     *
     * @param passwordAttributeName the name of the attribute
     */
    public void setPasswordAttributeName(String passwordAttributeName) {
        this.passwordAttributeName = passwordAttributeName;
    }

    /**
     * The names of any attributes in the user's  entry which represent application
     * roles. These will be converted to <tt>GrantedAuthority</tt>s and added to the
     * list in the returned LdapUserDetails object.
     *
     * @param roleAttributes the names of the role attributes.
     */
    public void setRoleAttributes(String[] roleAttributes) {
        Assert.notNull(roleAttributes, "roleAttributes array cannot be null");
        this.roleAttributes = roleAttributes;
    }

    /**
     * The prefix that should be applied to the role names
     * @param rolePrefix the prefix (defaults to "ROLE_").
     */
    public void setRolePrefix(String rolePrefix) {
        this.rolePrefix = rolePrefix;
    }
}
