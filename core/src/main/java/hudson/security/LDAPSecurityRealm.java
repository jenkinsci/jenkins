package hudson.security;

import com.sun.jndi.ldap.LdapCtxFactory;
import groovy.lang.Binding;
import hudson.Util;
import hudson.tasks.MailAddressResolver;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.User;
import hudson.util.FormFieldValidator;
import hudson.util.Scrambler;
import hudson.util.spring.BeanBuilder;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.userdetails.UserDetailsService;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.acegisecurity.userdetails.ldap.LdapUserDetails;
import org.acegisecurity.ldap.search.FilterBasedLdapUserSearch;
import org.acegisecurity.ldap.LdapUserSearch;
import org.acegisecurity.ldap.LdapDataAccessException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.dao.DataAccessException;

import javax.naming.NamingException;
import javax.naming.Context;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.servlet.ServletException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * {@link SecurityRealm} implementation that uses LDAP for authentication.
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
     * Normally "ou=groups"
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

    @DataBoundConstructor
    public LDAPSecurityRealm(String server, String rootDN, String userSearchBase, String userSearch, String groupSearchBase, String managerDN, String managerPassword) {
        this.server = server.trim();
        if(Util.fixEmptyAndTrim(rootDN)==null)    rootDN=Util.fixNull(inferRootDN(server));
        this.rootDN = rootDN.trim();
        this.userSearchBase = userSearchBase.trim();
        if(Util.fixEmptyAndTrim(userSearch)==null)    userSearch="uid={0}";
        this.userSearch = userSearch.trim();
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
        correctAuthoritiesPopulator(appContext);

        return new SecurityComponents(
            findBean(AuthenticationManager.class, appContext),
            new UserDetailsService() {
                final LdapUserSearch ldapSerach = findBean(LdapUserSearch.class, appContext);
                public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
                    try {
                        return ldapSerach.searchForUser(username);
                    } catch (LdapDataAccessException e) {
                        LOGGER.log(Level.WARNING, "Failed to search LDAP for username="+username,e);
                        throw new UserMayOrMayNotExistException(e.getMessage(),e);
                    }
                }
            });
    }

    /**
     * Adjust the authoritiesPopulator bean to have the correct groupSearchBase
     * @param appContext 
     */
    private void correctAuthoritiesPopulator(WebApplicationContext appContext) {
        DeferredCreationLdapAuthoritiesPopulator factory = (DeferredCreationLdapAuthoritiesPopulator) appContext.getBean("authoritiesPopulator");
        factory.setGroupSearchBase(groupSearchBase==null ? "" : groupSearchBase);
    }
    
    /**
     * If the security realm is LDAP, try to pick up e-mail address from LDAP.
     */
    public static final class MailAdressResolverImpl extends MailAddressResolver {
        public String findMailAddressFor(User u) {
            // LDAP not active
            Hudson hudson = Hudson.getInstance();
            if(!(hudson.getSecurityRealm() instanceof LDAPSecurityRealm))
                return null;
            try {
                LdapUserDetails details = (LdapUserDetails) hudson.getSecurityRealm().getSecurityComponents().userDetails.loadUserByUsername(u.getId());
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

    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<SecurityRealm> {
        public String getDisplayName() {
            return Messages.LDAPSecurityRealm_DisplayName();
        }

        public void doServerCheck(StaplerRequest req, StaplerResponse rsp, @QueryParameter final String server,
        		@QueryParameter final String managerDN,
        		@QueryParameter final String managerPassword
        		) throws IOException, ServletException {
            new FormFieldValidator(req,rsp,true) {
                protected void check() throws IOException, ServletException {
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
                        ok();   // connected
                    } catch (NamingException e) {
                        // trouble-shoot
                        Matcher m = Pattern.compile("([^:]+)(?:\\:(\\d+))?").matcher(server.trim());
                        if(!m.matches()) {
                            error("Syntax of this field is SERVER or SERVER:PORT");
                            return;
                        }

                        try {
                            InetAddress adrs = InetAddress.getByName(m.group(1));
                            int port=389;
                            if(m.group(2)!=null)
                                port = Integer.parseInt(m.group(2));
                            Socket s = new Socket(adrs,port);
                            s.close();
                        } catch (UnknownHostException x) {
                            error("Unknown host: "+x.getMessage());
                            return;
                        } catch (IOException x) {
                            error("Unable to connect to "+server+" : "+x.getMessage());
                            return;
                        }

                        // otherwise we don't know what caused it, so fall back to the general error report
                        // getMessage() alone doesn't offer enough
                        error("Unable to connect to "+server+": "+e);
                    } catch (NumberFormatException x) {
                        // The getLdapCtxInstance method throws this if it fails to parse the port number
                        error("Invalid port number");
                    }
                }
            }.check();
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

    static {
        LIST.add(DESCRIPTOR);
    }

    private static final Logger LOGGER = Logger.getLogger(LDAPSecurityRealm.class.getName());
}
