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

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.userdetails.UserDetails;

import javax.servlet.http.HttpSession;
import jenkins.security.NonSerializableSecurityContext;

/**
 * {@link UserDetails} that can mark {@link Authentication} invalid.
 *
 * <p>
 * Tomcat persists sessions by using Java serialization (and
 * that includes the security token created by Acegi, which includes this object)
 * and when that happens, the next time the server comes back
 * it will try to deserialize {@link SecurityContext} that Acegi
 * puts into {@link HttpSession} (which transitively includes {@link UserDetails}
 * that can be implemented by Hudson.
 *
 * <p>
 * Such {@link UserDetails} implementation can override the {@link #isInvalid()}
 * method and return false, so that such {@link SecurityContext} will be
 * dropped before the rest of Acegi sees it.
 *
 * <p>
 * See JENKINS-1482
 * 
 * @author Kohsuke Kawaguchi
 * @deprecated
 *      Starting 1.285, Hudson stops persisting {@link Authentication} altogether
 *      (see {@link NonSerializableSecurityContext}), so there's no need to use this mechanism.
 */
@Deprecated
public interface InvalidatableUserDetails extends UserDetails {
    boolean isInvalid();
}
