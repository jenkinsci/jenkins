/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.Util;
import hudson.Extension;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.acegisecurity.userdetails.UserDetailsService;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.User;
import org.jvnet.libpam.PAM;
import org.jvnet.libpam.PAMException;
import org.jvnet.libpam.UnixUser;
import org.jvnet.libpam.impl.CLibrary;
import org.springframework.dao.DataAccessException;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Set;

/**
 * {@link SecurityRealm} that uses Unix PAM authentication.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.282
 */
public class PAMSecurityRealm extends SecurityRealm {
    public final String serviceName;

    @DataBoundConstructor
    public PAMSecurityRealm(String serviceName) {
        serviceName = Util.fixEmptyAndTrim(serviceName);
        if(serviceName==null)   serviceName="sshd"; // use sshd as the default
        this.serviceName = serviceName;
    }

    public SecurityComponents createSecurityComponents() {
        return new SecurityComponents(
            new AuthenticationManager() {
                public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                    String username = authentication.getPrincipal().toString();
                    String password = authentication.getCredentials().toString();

                    try {
                        UnixUser u = new PAM(serviceName).authenticate(username, password);
                        Set<String> grps = u.getGroups();
                        GrantedAuthority[] groups = new GrantedAuthority[grps.size()];
                        int i=0;
                        for (String g : grps)
                            groups[i++] = new GrantedAuthorityImpl(g);
                        
                        // I never understood why Acegi insists on keeping the password...
                        return new UsernamePasswordAuthenticationToken(username, password, groups);
                    } catch (PAMException e) {
                        throw new BadCredentialsException(e.getMessage(),e);
                    }
                }
            },
            new UserDetailsService() {
                public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
                    if(!UnixUser.exists(username))
                        throw new UsernameNotFoundException("No such Unix user: "+username);
                    // return some dummy instance
                    return new User(username,"",true,true,true,true,
                            new GrantedAuthority[]{AUTHENTICATED_AUTHORITY});
                }
            }
        );
    }

    @Override
    public GroupDetails loadGroupByGroupname(final String groupname) throws UsernameNotFoundException, DataAccessException {
        if(CLibrary.libc.getgrnam(groupname)==null)
            throw new UsernameNotFoundException(groupname);
        return new GroupDetails() {
            @Override
            public String getName() {
                return groupname;
            }
        };
    }

    public static final class DescriptorImpl extends Descriptor<SecurityRealm> {
        public String getDisplayName() {
            return Messages.PAMSecurityRealm_DisplayName();
        }
    }

    @Extension
    public static DescriptorImpl install() {
        if(!Hudson.isWindows()) return new DescriptorImpl();
        return null;
    }
}
