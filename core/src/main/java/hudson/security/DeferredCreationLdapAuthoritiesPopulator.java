/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
/**
 * 
 */
package hudson.security;

import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.ldap.InitialDirContextFactory;
import org.acegisecurity.ldap.LdapDataAccessException;
import org.acegisecurity.providers.ldap.LdapAuthoritiesPopulator;
import org.acegisecurity.providers.ldap.populator.DefaultLdapAuthoritiesPopulator;
import org.acegisecurity.userdetails.ldap.LdapUserDetails;
import hudson.security.SecurityRealm.SecurityComponents;

/**
 * Implementation of {@link LdapAuthoritiesPopulator} that defers creation of a
 * {@link DefaultLdapAuthoritiesPopulator} until one is needed. This is done to
 * ensure that the groupSearchBase property can be set.
 * 
 * @author justinedelson
 * @deprecated as of 1.280
 *      {@link SecurityComponents} are now created after {@link SecurityRealm} is created, so
 *      the initialization order issue that this code was trying to address no longer exists.
 */
@Deprecated
public class DeferredCreationLdapAuthoritiesPopulator implements LdapAuthoritiesPopulator {

    /**
     * A default role which will be assigned to all authenticated users if set.
     */
    private String defaultRole = null;

    /**
     * An initial context factory is only required if searching for groups is
     * required.
     */
    private InitialDirContextFactory initialDirContextFactory = null;

    /**
     * Controls used to determine whether group searches should be performed
     * over the full sub-tree from the base DN.
     */
    private boolean searchSubtree = false;

    /**
     * The ID of the attribute which contains the role name for a group
     */
    private String groupRoleAttribute = "cn";

    /**
     * The base DN from which the search for group membership should be
     * performed
     */
    private String groupSearchBase = null;

    /**
     * The pattern to be used for the user search. {0} is the user's DN
     */
    private String groupSearchFilter = "(| (member={0}) (uniqueMember={0}) (memberUid={0}))";

    private String rolePrefix = "ROLE_";

    private boolean convertToUpperCase = true;

    /**
     * Constructor.
     * 
     * @param initialDirContextFactory
     *            supplies the contexts used to search for user roles.
     * @param groupSearchBase
     *            if this is an empty string the search will be performed from
     *            the root DN of the context factory.
     */
    public DeferredCreationLdapAuthoritiesPopulator(
            InitialDirContextFactory initialDirContextFactory, String groupSearchBase) {
        this.setInitialDirContextFactory(initialDirContextFactory);
        this.setGroupSearchBase(groupSearchBase);
    }

    public GrantedAuthority[] getGrantedAuthorities(LdapUserDetails userDetails)
            throws LdapDataAccessException {
        return create().getGrantedAuthorities(userDetails);
    }

    public void setConvertToUpperCase(boolean convertToUpperCase) {
        this.convertToUpperCase = convertToUpperCase;
    }

    public void setDefaultRole(String defaultRole) {
        this.defaultRole = defaultRole;
    }

    public void setGroupRoleAttribute(String groupRoleAttribute) {
        this.groupRoleAttribute = groupRoleAttribute;
    }

    public void setGroupSearchBase(String groupSearchBase) {
        this.groupSearchBase = groupSearchBase;
    }

    public void setGroupSearchFilter(String groupSearchFilter) {
        this.groupSearchFilter = groupSearchFilter;
    }

    public void setInitialDirContextFactory(InitialDirContextFactory initialDirContextFactory) {
        this.initialDirContextFactory = initialDirContextFactory;
    }

    public void setRolePrefix(String rolePrefix) {
        this.rolePrefix = rolePrefix;
    }

    public void setSearchSubtree(boolean searchSubtree) {
        this.searchSubtree = searchSubtree;
    }

    /**
     * Create a new DefaultLdapAuthoritiesPopulator object.
     * 
     * @return a DefaultLdapAuthoritiesPopulator.
     */
    private DefaultLdapAuthoritiesPopulator create() {
        DefaultLdapAuthoritiesPopulator populator = new DefaultLdapAuthoritiesPopulator(
                initialDirContextFactory, groupSearchBase);
        populator.setConvertToUpperCase(convertToUpperCase);
        if (defaultRole != null) {
            populator.setDefaultRole(defaultRole);
        }
        populator.setGroupRoleAttribute(groupRoleAttribute);
        populator.setGroupSearchFilter(groupSearchFilter);
        populator.setRolePrefix(rolePrefix);
        populator.setSearchSubtree(searchSubtree);
        return populator;
    }

}
