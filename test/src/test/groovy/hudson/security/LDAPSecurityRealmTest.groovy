/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems
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
package hudson.security

import org.jvnet.hudson.test.HudsonTestCase
import hudson.security.LDAPSecurityRealm.LDAPUserDetailsService
import org.acegisecurity.ldap.LdapUserSearch
import org.acegisecurity.userdetails.ldap.LdapUserDetailsImpl
import javax.naming.directory.BasicAttributes
import org.acegisecurity.providers.ldap.LdapAuthoritiesPopulator
import org.acegisecurity.GrantedAuthority

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class LDAPSecurityRealmTest extends HudsonTestCase {
    /**
     * This minimal test still causes the 'LDAPBindSecurityRealm.groovy' to be parsed, allowing us to catch
     * basic syntax errors and such.
     */
    void testGroovyBeanDef() {
        hudson.securityRealm = new LDAPSecurityRealm("ldap.itd.umich.edu",null,null,null,null,null,null);
        println hudson.securityRealm.securityComponents // force the component creation
    }

    void testSessionStressTest() {
        LDAPUserDetailsService s = new LDAPUserDetailsService(
                { username ->
                    def e = new LdapUserDetailsImpl.Essence();
                    e.username = username;
                    def ba = new BasicAttributes()
                    ba.put("test",username);
                    ba.put("xyz","def");
                    e.attributes = ba;
                    return e.createUserDetails();
                } as LdapUserSearch,
                { details -> new GrantedAuthority[0] } as LdapAuthoritiesPopulator);
        def d1 = s.loadUserByUsername("me");
        def d2 = s.loadUserByUsername("you");
        def d3 = s.loadUserByUsername("me");
        // caching should reuse the same attributes
        assertSame(d1.attributes,d3.attributes);
        assertNotSame(d1.attributes,d2.attributes);
    }
}