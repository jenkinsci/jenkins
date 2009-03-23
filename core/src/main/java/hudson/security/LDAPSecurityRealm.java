/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe
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
package hudson.security;

import com.sun.jndi.ldap.LdapCtxFactory;
import groovy.lang.Binding;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.User;
import hudson.tasks.MailAddressResolver;
import hudson.util.FormValidation;
import hudson.util.Scrambler;
import hudson.util.spring.BeanBuilder;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.ldap.InitialDirContextFactory;
import org.acegisecurity.ldap.LdapDataAccessException;
import org.acegisecurity.ldap.LdapTemplate;
import org.acegisecurity.ldap.LdapUserSearch;
import org.acegisecurity.ldap.search.FilterBasedLdapUserSearch;
import org.acegisecurity.providers.ldap.LdapAuthoritiesPopulator;
import org.acegisecurity.providers.ldap.populator.DefaultLdapAuthoritiesPopulator;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UserDetailsService;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.acegisecurity.userdetails.ldap.LdapUserDetails;
import org.acegisecurity.userdetails.ldap.LdapUserDetailsImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.web.context.WebApplicationContext;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.servlet.ServletException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * {@link SecurityRealm} implementation that uses LDAP for authentication.
 *
 *
 * <h2>Key Object Classes</h2>
 *
 * <h4>Group Membership</h4>
 *
 * <p>
 * Two object classes seem to be relevant. These are in RFC 2256 and core.schema. These use DN for membership,
 * so it can create a group of anything. I don't know what the difference between these two are.
 * <pre>
   attributetype ( 2.5.4.31 NAME 'member'
     DESC 'RFC2256: member of a group'
     SUP distinguishedName )

   attributetype ( 2.5.4.50 NAME 'uniqueMember'
     DESC 'RFC2256: unique member of a group'
     EQUALITY uniqueMemberMatch
     SYNTAX 1.3.6.1.4.1.1466.115.121.1.34 )

   objectclass ( 2.5.6.9 NAME 'groupOfNames'
     DESC 'RFC2256: a group of names (DNs)'
     SUP top STRUCTURAL
     MUST ( member $ cn )
     MAY ( businessCategory $ seeAlso $ owner $ ou $ o $ description ) )

   objectclass ( 2.5.6.17 NAME 'groupOfUniqueNames'
     DESC 'RFC2256: a group of unique names (DN and Unique Identifier)'
     SUP top STRUCTURAL
     MUST ( uniqueMember $ cn )
     MAY ( businessCategory $ seeAlso $ owner $ ou $ o $ description ) )
 * </pre>
 *
 * <p>
 * This one is from nis.schema, and appears to model POSIX group/user thing more closely.
 * <pre>
   objectclass ( 1.3.6.1.1.1.2.2 NAME 'posixGroup'
     DESC 'Abstraction of a group of accounts'
     SUP top STRUCTURAL
     MUST ( cn $ gidNumber )
     MAY ( userPassword $ memberUid $ description ) )

   attributetype ( 1.3.6.1.1.1.1.12 NAME 'memberUid'
     EQUALITY caseExactIA5Match
     SUBSTR caseExactIA5SubstringsMatch
     SYNTAX 1.3.6.1.4.1.1466.115.121.1.26 )

   objectclass ( 1.3.6.1.1.1.2.0 NAME 'posixAccount'
     DESC 'Abstraction of an account with POSIX attributes'
     SUP top AUXILIARY
     MUST ( cn $ uid $ uidNumber $ gidNumber $ homeDirectory )
     MAY ( userPassword $ loginShell $ gecos $ description ) )

   attributetype ( 1.3.6.1.1.1.1.0 NAME 'uidNumber'
     DESC 'An integer uniquely identifying a user in an administrative domain'
     EQUALITY integerMatch
     SYNTAX 1.3.6.1.4.1.1466.115.121.1.27 SINGLE-VALUE )

   attributetype ( 1.3.6.1.1.1.1.1 NAME 'gidNumber'
     DESC 'An integer uniquely identifying a group in an administrative domain'
     EQUALITY integerMatch
     SYNTAX 1.3.6.1.4.1.1466.115.121.1.27 SINGLE-VALUE )
 * </pre>
 *
 * <p>
 * Active Directory specific schemas (from <a href="http://www.grotan.com/ldap/microsoft.schema">here</a>).
 * <pre>
   objectclass ( 1.2.840.113556.1.5.8
     NAME 'group'
     SUP top
     STRUCTURAL
     MUST (groupType )
     MAY (member $ nTGroupMembers $ operatorCount $ adminCount $
         groupAttributes $ groupMembershipSAM $ controlAccessRights $
         desktopProfile $ nonSecurityMember $ managedBy $
         primaryGroupToken $ mail ) )

   objectclass ( 1.2.840.113556.1.5.9
     NAME 'user'
     SUP organizationalPerson
     STRUCTURAL
     MAY (userCertificate $ networkAddress $ userAccountControl $
         badPwdCount $ codePage $ homeDirectory $ homeDrive $
         badPasswordTime $ lastLogoff $ lastLogon $ dBCSPwd $
         localeID $ scriptPath $ logonHours $ logonWorkstation $
         maxStorage $ userWorkstations $ unicodePwd $
         otherLoginWorkstations $ ntPwdHistory $ pwdLastSet $
         preferredOU $ primaryGroupID $ userParameters $
         profilePath $ operatorCount $ adminCount $ accountExpires $
         lmPwdHistory $ groupMembershipSAM $ logonCount $
         controlAccessRights $ defaultClassStore $ groupsToIgnore $
         groupPriority $ desktopProfile $ dynamicLDAPServer $
         userPrincipalName $ lockoutTime $ userSharedFolder $
         userSharedFolderOther $ servicePrincipalName $
         aCSPolicyName $ terminalServer $ mSMQSignCertificates $
         mSMQDigests $ mSMQDigestsMig $ mSMQSignCertificatesMig $
         msNPAllowDialin $ msNPCallingStationID $
         msNPSavedCallingStationID $ msRADIUSCallbackNumber $
         msRADIUSFramedIPAddress $ msRADIUSFramedRoute $
         msRADIUSServiceType $ msRASSavedCallbackNumber $
         msRASSavedFramedIPAddress $ msRASSavedFramedRoute $
         mS-DS-CreatorSID ) )
 * </pre>
 *
 *
 * <h2>References</h2>
 * <dl>
 * <dt><a href="http://www.openldap.org/doc/admin22/schema.html">Standard Schemas</a>
 * <dd>
 * The downloadable distribution contains schemas that define the structure of LDAP entries.
 * Because this is a standard, we expect most LDAP servers out there to use it, although
 * there are different objectClasses that can be used for similar purposes, and apparently
 * many deployments choose to use different objectClasses.
 *
 * <dt><a href="http://www.ietf.org/rfc/rfc2256.txt">RFC 2256</a>
 * <dd>
 * Defines the meaning of several key datatypes used in the schemas with some explanations. 
 *
 * <dt><a href="http://msdn.microsoft.com/en-us/library/ms675085(VS.85).aspx">Active Directory schema</a>
 * <dd>
 * More navigable schema list, including core and MS extensions specific to Active Directory.
 * </dl>
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.166
 */
public class LDAPSecurityRealm extends SecurityRealm {
    /**
     * LDAP server name, optionally with TCP port number, like "ldap.acme.org"
     * or "ldap.acme.org:389".
     */
    public final String server;

    /**
     * The root DN to connect to. Normally something like "dc=sun,dc=com"
     *
     * How do I infer this?
     */
    public final String rootDN;

    /**
     * Specifies the relative DN from {@link #rootDN the root DN}.
     * This is used to narrow down the search space when doing user search.
     *
     * Something like "ou=people" but can be empty.
     */
    public final String userSearchBase;

    /**
     * Query to locate an entry that identifies the user, given the user name string.
     *
     * Normally "uid={0}"
     *
     * @see FilterBasedLdapUserSearch
     */
    public final String userSearch;
    
    /**
     * This defines the organizational unit that contains groups.
     *
     * Normally "" to indicate the full LDAP search, but can be often narrowed down to
     * something like "ou=groups"
     *
     * @see FilterBasedLdapUserSearch
     */
    public final String groupSearchBase;

    /*
        Other configurations that are needed:

        group search base DN (relative to root DN)
        group search filter (uniquemember={1} seems like a reasonable default)
        group target (CN is a reasonable default)

        manager dn/password if anonyomus search is not allowed.

        See GF configuration at http://weblogs.java.net/blog/tchangu/archive/2007/01/ldap_security_r.html
        Geronimo configuration at http://cwiki.apache.org/GMOxDOC11/ldap-realm.html
     */

    /**
     * If non-null, we use this and {@link #managerPassword}
     * when binding to LDAP.
     *
     * This is necessary when LDAP doesn't support anonymous access.
     */
    public final String managerDN;

    /**
     * Scrambled password, used to first bind to LDAP.
     */
    private final String managerPassword;

    /**
     * Created in {@link #createSecurityComponents()}. Can be used to connect to LDAP.
     */
    private transient LdapTemplate ldapTemplate;

    @DataBoundConstructor
    public LDAPSecurityRealm(String server, String rootDN, String userSearchBase, String userSearch, String groupSearchBase, String managerDN, String managerPassword) {
        this.server = server.trim();
        if(Util.fixEmptyAndTrim(rootDN)==null)    rootDN=Util.fixNull(inferRootDN(server));
        this.rootDN = rootDN.trim();
        this.userSearchBase = userSearchBase.trim();
        userSearch = Util.fixEmptyAndTrim(userSearch);
        this.userSearch = userSearch!=null ? userSearch : "uid={0}";
        this.groupSearchBase = Util.fixEmptyAndTrim(groupSearchBase);
        this.managerDN = Util.fixEmpty(managerDN);
        this.managerPassword = Scrambler.scramble(Util.fixEmpty(managerPassword));
    }

    public String getServerUrl() {
        return addPrefix(server);
    }

    /**
     * Infer the root DN.
     *
     * @return null if not found.
     */
    private String inferRootDN(String server) {
        try {
            Hashtable<String,String> props = new Hashtable<String,String>();
            if(managerDN!=null) {
                props.put(Context.SECURITY_PRINCIPAL,managerDN);
                props.put(Context.SECURITY_CREDENTIALS,getManagerPassword());
            }
            DirContext ctx = LdapCtxFactory.getLdapCtxInstance(getServerUrl()+'/', props);
            Attributes atts = ctx.getAttributes("");
            Attribute a = atts.get("defaultNamingContext");
            if(a!=null) // this entry is available on Active Directory. See http://msdn2.microsoft.com/en-us/library/ms684291(VS.85).aspx
                return a.toString();
            
            a = atts.get("namingcontexts");
            if(a==null) {
                LOGGER.warning("namingcontexts attribute not found in root DSE of "+server);
                return null;
            }
            return a.get().toString();
        } catch (NamingException e) {
            LOGGER.log(Level.WARNING,"Failed to connect to LDAP to infer Root DN for "+server,e);
            return null;
        }
    }

    public String getManagerPassword() {
        return Scrambler.descramble(managerPassword);
    }

    public String getLDAPURL() {
        return getServerUrl()+'/'+Util.fixNull(rootDN);
    }

    public SecurityComponents createSecurityComponents() {
        Binding binding = new Binding();
        binding.setVariable("instance", this);

        BeanBuilder builder = new BeanBuilder();
        builder.parse(Hudson.getInstance().servletContext.getResourceAsStream("/WEB-INF/security/LDAPBindSecurityRealm.groovy"),binding);
        final WebApplicationContext appContext = builder.createApplicationContext();

        ldapTemplate = new LdapTemplate(findBean(InitialDirContextFactory.class, appContext));

        return new SecurityComponents(
            findBean(AuthenticationManager.class, appContext),
            new UserDetailsService() {
                final LdapUserSearch ldapSearch = findBean(LdapUserSearch.class, appContext);
                final LdapAuthoritiesPopulator authoritiesPopulator = findBean(LdapAuthoritiesPopulator.class, appContext);
                public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
                    try {
                        LdapUserDetails ldapUser = ldapSearch.searchForUser(username);
                        // LdapUserSearch does not populate granted authorities (group search).
                        // Add those, as done in LdapAuthenticationProvider.createUserDetails().
                        if (ldapUser != null) {
                            LdapUserDetailsImpl.Essence user = new LdapUserDetailsImpl.Essence(ldapUser);
                            GrantedAuthority[] extraAuthorities = authoritiesPopulator.getGrantedAuthorities(ldapUser);
                            for (int i = 0; i < extraAuthorities.length; i++) {
                                user.addAuthority(extraAuthorities[i]);
                            }
                            ldapUser = user.createUserDetails();
                        }
                        return ldapUser;
                    } catch (LdapDataAccessException e) {
                        LOGGER.log(Level.WARNING, "Failed to search LDAP for username="+username,e);
                        throw new UserMayOrMayNotExistException(e.getMessage(),e);
                    }
                }
            });
    }

    @Override
    public GroupDetails loadGroupByGroupname(String groupname) throws UsernameNotFoundException, DataAccessException {
        // TODO: obtain a DN instead so that we can obtain multiple attributes later
        String searchBase = groupSearchBase != null ? groupSearchBase : "";
        final Set<String> groups = (Set<String>)ldapTemplate.searchForSingleAttributeValues(searchBase, GROUP_SEARCH,
                new String[]{groupname}, "cn");

        if(groups.isEmpty())
            throw new UsernameNotFoundException(groupname);

        return new GroupDetails() {
            public String getName() {
                return groups.iterator().next();
            }
        };
    }

    /**
     * If the security realm is LDAP, try to pick up e-mail address from LDAP.
     */
    @Extension
    public static final class MailAdressResolverImpl extends MailAddressResolver {
        public String findMailAddressFor(User u) {
            // LDAP not active
            SecurityRealm realm = Hudson.getInstance().getSecurityRealm();
            if(!(realm instanceof LDAPSecurityRealm))
                return null;
            try {
                LdapUserDetails details = (LdapUserDetails)realm.getSecurityComponents().userDetails.loadUserByUsername(u.getId());
                Attribute mail = details.getAttributes().get("mail");
                if(mail==null)  return null;    // not found
                return (String)mail.get();
            } catch (UsernameNotFoundException e) {
                LOGGER.log(Level.FINE, "Failed to look up LDAP for e-mail address",e);
                return null;
            } catch (DataAccessException e) {
                LOGGER.log(Level.FINE, "Failed to look up LDAP for e-mail address",e);
                return null;
            } catch (NamingException e) {
                LOGGER.log(Level.FINE, "Failed to look up LDAP for e-mail address",e);
                return null;
            }
        }
    }

    /**
     * {@link LdapAuthoritiesPopulator} that adds the automatic 'authenticated' role.
     */
    public static final class AuthoritiesPopulatorImpl extends DefaultLdapAuthoritiesPopulator {
        public AuthoritiesPopulatorImpl(InitialDirContextFactory initialDirContextFactory, String groupSearchBase) {
            super(initialDirContextFactory, groupSearchBase);
        }

        @Override
        protected Set getAdditionalRoles(LdapUserDetails ldapUser) {
            return Collections.singleton(AUTHENTICATED_AUTHORITY);
        }
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<SecurityRealm> {
        public String getDisplayName() {
            return Messages.LDAPSecurityRealm_DisplayName();
        }

        public FormValidation doServerCheck(
                @QueryParameter final String server,
        		@QueryParameter final String managerDN,
        		@QueryParameter final String managerPassword) {

            if(!Hudson.getInstance().hasPermission(Hudson.ADMINISTER))
                return FormValidation.ok();

            try {
                Hashtable<String,String> props = new Hashtable<String,String>();
                if(managerDN!=null && managerDN.trim().length() > 0  && !"undefined".equals(managerDN)) {
                    props.put(Context.SECURITY_PRINCIPAL,managerDN);
                }
                if(managerPassword!=null && managerPassword.trim().length() > 0 && !"undefined".equals(managerPassword)) {
                    props.put(Context.SECURITY_CREDENTIALS,managerPassword);
                }
                DirContext ctx = LdapCtxFactory.getLdapCtxInstance(addPrefix(server)+'/', props);
                ctx.getAttributes("");
                return FormValidation.ok();   // connected
            } catch (NamingException e) {
                // trouble-shoot
                Matcher m = Pattern.compile("(ldaps://)?([^:]+)(?:\\:(\\d+))?").matcher(server.trim());
                if(!m.matches())
                    return FormValidation.error("Syntax of server field is SERVER or SERVER:PORT or ldaps://SERVER[:PORT]");

                try {
                    InetAddress adrs = InetAddress.getByName(m.group(2));
                    int port = m.group(1)!=null ? 636 : 389;
                    if(m.group(3)!=null)
                        port = Integer.parseInt(m.group(3));
                    Socket s = new Socket(adrs,port);
                    s.close();
                } catch (UnknownHostException x) {
                    return FormValidation.error("Unknown host: "+x.getMessage());
                } catch (IOException x) {
                    return FormValidation.error("Unable to connect to "+server+" : "+x.getMessage());
                }

                // otherwise we don't know what caused it, so fall back to the general error report
                // getMessage() alone doesn't offer enough
                return FormValidation.error("Unable to connect to "+server+": "+e);
            } catch (NumberFormatException x) {
                // The getLdapCtxInstance method throws this if it fails to parse the port number
                return FormValidation.error("Invalid port number");
            }
        }
    }

    /**
     * If the given "server name" is just a host name (plus optional host name), add ldap:// prefix.
     * Otherwise assume it already contains the scheme, and leave it intact.
     */
    private static String addPrefix(String server) {
        if(server.contains("://"))  return server;
        else    return "ldap://"+server;
    }

    private static final Logger LOGGER = Logger.getLogger(LDAPSecurityRealm.class.getName());

    /**
     * LDAP filter to look for groups by their names.
     *
     * "{0}" is the group name as given by the user.
     * See http://msdn.microsoft.com/en-us/library/aa746475(VS.85).aspx for the syntax by example.
     * WANTED: The specification of the syntax.
     */
    public static String GROUP_SEARCH = System.getProperty(LDAPSecurityRealm.class.getName()+".groupSearch",
            "(& (cn={0}) (| (objectclass=groupOfNames) (objectclass=groupOfUniqueNames) (objectclass=posixGroup)))");
}
