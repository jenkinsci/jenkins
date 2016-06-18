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

import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

/**
 * {@link AuthorizationStrategy} that grants full-control to authenticated user
 * and optionally read access to anonymous users
 *
 * @author Kohsuke Kawaguchi
 */
public class FullControlOnceLoggedInAuthorizationStrategy extends AuthorizationStrategy {
    /**
     * Whether to allow anonymous read access, for backward compatibility
     * default is to allow it
     */
    private boolean denyAnonymousReadAccess = false;
    
    @DataBoundConstructor
    public FullControlOnceLoggedInAuthorizationStrategy() {
    }

    @Override
    public ACL getRootACL() {
        return denyAnonymousReadAccess ? AUTHENTICATED_READ : ANONYMOUS_READ;
    }

    public List<String> getGroups() {
        return Collections.emptyList();
    }
    
    /**
     * If true, anonymous read access will be allowed
     */
    public boolean isAllowAnonymousRead() {
        return !denyAnonymousReadAccess;
    }
    
    @DataBoundSetter
    public void setAllowAnonymousRead(boolean allowAnonymousRead) {
        this.denyAnonymousReadAccess = !allowAnonymousRead;
    }

    private static final SparseACL AUTHENTICATED_READ = new SparseACL(null);
    private static final SparseACL ANONYMOUS_READ = new SparseACL(null);

    static {
        ANONYMOUS_READ.add(ACL.EVERYONE, Jenkins.ADMINISTER,true);
        ANONYMOUS_READ.add(ACL.ANONYMOUS, Jenkins.ADMINISTER,false);
        ANONYMOUS_READ.add(ACL.ANONYMOUS, Permission.READ,true);
        
        AUTHENTICATED_READ.add(ACL.EVERYONE, Jenkins.ADMINISTER, true);
        AUTHENTICATED_READ.add(ACL.ANONYMOUS, Jenkins.ADMINISTER, false);
    }

    /**
     * @deprecated as of 1.643
     *      Inject descriptor via {@link Inject}.
     */
    @Restricted(NoExternalUse.class)
    public static Descriptor<AuthorizationStrategy> DESCRIPTOR;

    @Extension @Symbol("loggedInUsersCanDoAnything")
    public static class DescriptorImpl extends Descriptor<AuthorizationStrategy> {
        public DescriptorImpl() {
            DESCRIPTOR = this;
        }

        public String getDisplayName() {
            return Messages.FullControlOnceLoggedInAuthorizationStrategy_DisplayName();
        }
    }
}
