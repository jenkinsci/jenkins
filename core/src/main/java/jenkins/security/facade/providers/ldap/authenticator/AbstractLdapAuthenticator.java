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

package jenkins.security.facade.providers.ldap.authenticator;

import jenkins.security.facade.userdetails.ldap.LdapUserDetailsMapper;
import org.acegisecurity.AcegiMessageSource;
import org.acegisecurity.ldap.InitialDirContextFactory;
import org.acegisecurity.ldap.LdapEntryMapper;
import org.acegisecurity.ldap.LdapUserSearch;
import org.acegisecurity.providers.ldap.LdapAuthenticator;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.util.Assert;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;


/**
 * Base class for the authenticator implementations.
 *
 * Copied from acegi-security
 */
public abstract class AbstractLdapAuthenticator implements LdapAuthenticator, InitializingBean, MessageSourceAware {
    //~ Instance fields ================================================================================================

    private InitialDirContextFactory initialDirContextFactory;
    private LdapUserDetailsMapper userDetailsMapper = new LdapUserDetailsMapper();

    /** Optional search object which can be used to locate a user when a simple DN match isn't sufficient */
    private LdapUserSearch userSearch;
    protected MessageSourceAccessor messages = AcegiMessageSource.getAccessor();

    /**
     * The suffix to be added to the DN patterns, worked out internally from the root DN of the configured
     * InitialDirContextFactory.
     */
    private String dnSuffix = "";

    /** The attributes which will be retrieved from the directory. Null means all attributes */
    private String[] userAttributes = null;

    //private String[] userDnPattern = null;
    /** Stores the patterns which are used as potential DN matches */
    private MessageFormat[] userDnFormat = null;

    //~ Constructors ===================================================================================================

    /**
     * Create an initialized instance to the {@link InitialDirContextFactory} provided.
     * 
     * @param initialDirContextFactory
     */
    public AbstractLdapAuthenticator(InitialDirContextFactory initialDirContextFactory) {
        this.setInitialDirContextFactory(initialDirContextFactory);
    }

    // ~ Methods
    // ========================================================================================================

    public void afterPropertiesSet() throws Exception {
        Assert.isTrue((userDnFormat != null) || (userSearch != null),
                "Either an LdapUserSearch or DN pattern (or both) must be supplied.");
    }

    /**
     * Set the {@link InitialDirContextFactory} and initialize this instance from its data.
     * 
     * @param initialDirContextFactory
     */
    private void setInitialDirContextFactory(InitialDirContextFactory initialDirContextFactory) {
        Assert.notNull(initialDirContextFactory, "initialDirContextFactory must not be null.");
        this.initialDirContextFactory = initialDirContextFactory;

        String rootDn = initialDirContextFactory.getRootDn();

        if (rootDn.length() > 0) {
            dnSuffix = "," + rootDn;
        }
    }

    protected InitialDirContextFactory getInitialDirContextFactory() {
        return initialDirContextFactory;
    }

    public String[] getUserAttributes() {
        return userAttributes;
    }

    protected LdapEntryMapper getUserDetailsMapper() {
        return userDetailsMapper;
    }

    /**
     * Builds list of possible DNs for the user, worked out from the <tt>userDnPatterns</tt> property. The
     * returned value includes the root DN of the provider URL used to configure the
     * <tt>InitialDirContextfactory</tt>.
     *
     * @param username the user's login name
     *
     * @return the list of possible DN matches, empty if <tt>userDnPatterns</tt> wasn't set.
     */
    protected List getUserDns(String username) {
        if (userDnFormat == null) {
            return new ArrayList(0);
        }

        List userDns = new ArrayList(userDnFormat.length);
        String[] args = new String[] {username};

        synchronized (userDnFormat) {
            for (int i = 0; i < userDnFormat.length; i++) {
                userDns.add(userDnFormat[i].format(args) + dnSuffix);
            }
        }

        return userDns;
    }

    protected LdapUserSearch getUserSearch() {
        return userSearch;
    }

    public void setMessageSource(MessageSource messageSource) {
        Assert.notNull("Message source must not be null");
        this.messages = new MessageSourceAccessor(messageSource);
    }

    /**
     * Sets the user attributes which will be retrieved from the directory.
     *
     * @param userAttributes
     */
    public void setUserAttributes(String[] userAttributes) {
        Assert.notNull(userAttributes, "The userAttributes property cannot be set to null");
        this.userAttributes = userAttributes;
    }

    public void setUserDetailsMapper(LdapUserDetailsMapper userDetailsMapper) {
        Assert.notNull("userDetailsMapper must not be null");
        this.userDetailsMapper = userDetailsMapper;
    }

    /**
     * Sets the pattern which will be used to supply a DN for the user. The pattern should be the name relative
     * to the root DN. The pattern argument {0} will contain the username. An example would be "cn={0},ou=people".
     *
     * @param dnPattern the array of patterns which will be tried when obtaining a username
     * to a DN.
     */
    public void setUserDnPatterns(String[] dnPattern) {
        Assert.notNull(dnPattern, "The array of DN patterns cannot be set to null");
//        this.userDnPattern = dnPattern;
        userDnFormat = new MessageFormat[dnPattern.length];

        for (int i = 0; i < dnPattern.length; i++) {
            userDnFormat[i] = new MessageFormat(dnPattern[i]);
        }
    }

    public void setUserSearch(LdapUserSearch userSearch) {
        Assert.notNull(userSearch, "The userSearch cannot be set to null");
        this.userSearch = userSearch;
    }
}
