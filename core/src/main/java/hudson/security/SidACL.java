package hudson.security;

import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.acegisecurity.acls.sid.GrantedAuthoritySid;
import org.acegisecurity.acls.sid.Sid;

/**
 * {@link ACL} that checks permissions based on {@link GrantedAuthority}
 * of the {@link Authentication}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class SidACL extends ACL {

    @Override
    public boolean hasPermission(Authentication a, Permission permission) {
        if(a==SYSTEM)   return true;
        Boolean b = _hasPermission(a,permission);
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
    protected Boolean _hasPermission(Authentication a, Permission permission) {
        // ACL entries for this principal takes precedence
        Boolean b = hasPermission(new PrincipalSid(a),permission);
        if(b!=null) return b;

        // after that, we check if the groups this principal belongs to
        // has any ACL entries.
        // here we are using GrantedAuthority as a group
        for(GrantedAuthority ga : a.getAuthorities()) {
            b = hasPermission(new GrantedAuthoritySid(ga),permission);
            if(b!=null) return b;
        }

        // finally everyone
        b = hasPermission(EVERYONE,permission);
        if(b!=null) return b;

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

    /**
     * Creates a new {@link SidACL} that first consults 'this' {@link SidACL} and then delegate to
     * the given parent {@link SidACL}. By doing this at the {@link SidACL} level and not at the
     * {@link ACL} level, this allows the child ACLs to have an explicit deny entry.
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
}
