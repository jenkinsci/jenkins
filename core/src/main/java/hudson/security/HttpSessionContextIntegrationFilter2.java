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
package hudson.security;

import jenkins.security.NonSerializableSecurityContext;
import org.acegisecurity.context.HttpSessionContextIntegrationFilter;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.Authentication;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Erases the {@link SecurityContext} persisted in {@link HttpSession}
 * if {@link InvalidatableUserDetails#isInvalid()} returns true.
 *
 * @see InvalidatableUserDetails
 */
public class HttpSessionContextIntegrationFilter2 extends HttpSessionContextIntegrationFilter {
    public HttpSessionContextIntegrationFilter2() throws ServletException {
        setContext(NonSerializableSecurityContext.class);
    }

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpSession session = ((HttpServletRequest) req).getSession(false);
        if(session!=null) {
            SecurityContext o = (SecurityContext)session.getAttribute(ACEGI_SECURITY_CONTEXT_KEY);
            if(o!=null) {
                Authentication a = o.getAuthentication();
                if(a!=null) {
                    if (a.getPrincipal() instanceof InvalidatableUserDetails) {
                        InvalidatableUserDetails ud = (InvalidatableUserDetails) a.getPrincipal();
                        if(ud.isInvalid())
                            // don't let Acegi see invalid security context
                            session.setAttribute(ACEGI_SECURITY_CONTEXT_KEY,null);
                    }
                }
            }
        }

        super.doFilter(req, res, chain);
    }
}
