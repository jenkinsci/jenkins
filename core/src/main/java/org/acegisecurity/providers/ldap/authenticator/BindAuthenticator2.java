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
package org.acegisecurity.providers.ldap.authenticator;

import org.acegisecurity.ldap.InitialDirContextFactory;
import org.acegisecurity.userdetails.ldap.LdapUserDetails;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * {@link BindAuthenticator} with improved diagnostics.
 * 
 * @author Kohsuke Kawaguchi
 */
public class BindAuthenticator2 extends BindAuthenticator {
    /**
     * If we ever had a successful authentication, 
     */
    private boolean hadSuccessfulAuthentication;

    public BindAuthenticator2(InitialDirContextFactory initialDirContextFactory) {
        super(initialDirContextFactory);
    }

    @Override
    public LdapUserDetails authenticate(String username, String password) {
        LdapUserDetails user = super.authenticate(username, password);
        hadSuccessfulAuthentication = true;
        return user;
    }

    @Override
    void handleBindException(String userDn, String username, Throwable cause) {
        LOGGER.log(hadSuccessfulAuthentication? Level.FINE : Level.WARNING,
            "Failed to bind to LDAP: userDn"+userDn+"  username="+username,cause);
        super.handleBindException(userDn, username, cause);
    }

    private static final Logger LOGGER = Logger.getLogger(BindAuthenticator2.class.getName());
}
