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
     */
    protected abstract Boolean hasPermission(Sid p, Permission permission);
}
