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
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.acegisecurity.acls.sid.GrantedAuthoritySid;
import org.acegisecurity.acls.sid.Sid;

import javax.annotation.Nonnull;
import java.util.logging.Logger;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;

/**
 * {@link ACL} that checks permissions based on {@link GrantedAuthority}
 * of the {@link Authentication}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class SidACL extends ACL {

    @Override
    public boolean hasPermission(@Nonnull Authentication a, Permission permission) {
        if(a==SYSTEM) {
            if(LOGGER.isLoggable(FINE))
                LOGGER.fine("hasPermission("+a+","+permission+")=>SYSTEM user has full access");
            return true;
        }
        Boolean b = _hasPermission(a,permission);

        if(LOGGER.isLoggable(FINE))
            LOGGER.fine("hasPermission("+a+","+permission+")=>"+(b==null?"null, thus false":b));

        if(b==null) b=false;    // default to rejection
        return b;
    }

    /**
     * Implementation that backs up {@link #hasPermission(Authentication, Permission)}.
     *
     * @return
     *      true or false if {@link #hasPermission(Sid, Permission)} returns it.
     *      Otherwise null, indicating that this ACL doesn't have any entry for it.
     */
    protected Boolean _hasPermission(@Nonnull Authentication a, Permission permission) {
        // ACL entries for this principal takes precedence
        Boolean b = hasPermission(new PrincipalSid(a),permission);
        if(LOGGER.isLoggable(FINER))
            LOGGER.finer("hasPermission(PrincipalSID:"+a.getPrincipal()+","+permission+")=>"+b);
        if(b!=null)
            return b;

        // after that, we check if the groups this principal belongs to
        // has any ACL entries.
        // here we are using GrantedAuthority as a group
        for(GrantedAuthority ga : a.getAuthorities()) {
            b = hasPermission(new GrantedAuthoritySid(ga),permission);
            if(LOGGER.isLoggable(FINER))
                LOGGER.finer("hasPermission(GroupSID:"+ga.getAuthority()+","+permission+")=>"+b);
            if(b!=null)
                return b;
        }

        // permissions granted to 'everyone' and 'anonymous' users are granted to everyone
        for (Sid sid : AUTOMATIC_SIDS) {
            b = hasPermission(sid,permission);
            if(LOGGER.isLoggable(FINER))
                LOGGER.finer("hasPermission("+sid+","+permission+")=>"+b);
            if(b!=null)
                return b;
        }

        return null;
    }

    /**
     * Checks if the given {@link Sid} has the given {@link Permission}.
     *
     * <p>
     * {@link #hasPermission(Authentication, Permission)} is implemented
     * by checking authentication's {@link GrantedAuthority} by using
     * this method.
     *
     * <p>
     * It is the implementor's responsibility to recognize {@link Permission#impliedBy}
     * and take that into account.
     *
     * @return
     *      true if the access should be granted, false if it should be denied.
     *      The null value indicates that the ACL does no rule for this Sid/Permission
     *      combination. The caller can decide what to do &mash; such as consulting the higher level ACL,
     *      or denying the access (if the model is no-access-by-default.)  
     */
    protected abstract Boolean hasPermission(Sid p, Permission permission);

    protected String toString(Sid p) {
        if (p instanceof GrantedAuthoritySid)
            return ((GrantedAuthoritySid) p).getGrantedAuthority();
        if (p instanceof PrincipalSid)
            return ((PrincipalSid) p).getPrincipal();
        if (p == EVERYONE)
            return "role_everyone";
        // hmm...
        return p.toString();
    }

    /**
     * Creates a new {@link SidACL} that first consults 'this' {@link SidACL} and then delegate to
     * the given parent {@link SidACL}. By doing this at the {@link SidACL} level and not at the
     * {@link ACL} level, this allows the child ACLs to have an explicit deny entry.
     * Note that the combined ACL calls hasPermission(Sid,Permission) in the child and parent
     * SidACLs directly, so if these override _hasPermission then this custom behavior will
     * not be applied.
     */
    public final SidACL newInheritingACL(final SidACL parent) {
        final SidACL child = this;
        return new SidACL() {
            protected Boolean hasPermission(Sid p, Permission permission) {
                Boolean b = child.hasPermission(p, permission);
                if(b!=null) return b;
                return parent.hasPermission(p,permission);
            }
        };
    }

    private static final Logger LOGGER = Logger.getLogger(SidACL.class.getName());
}
