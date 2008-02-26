package hudson.security;

import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.acls.sid.GrantedAuthoritySid;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.acegisecurity.acls.sid.Sid;

import java.util.ArrayList;
import java.util.List;

/**
 * Accses control list.
 *
 * @author Kohsuke Kawaguchi
 */
public class SparseACL extends SidACL {
    public static final class Entry {
        // Sid has value-equality semantics
        public final Sid sid;
        public final Permission permission;
        public final boolean allowed;

        public Entry(Sid sid, Permission permission, boolean allowed) {
            this.sid = sid;
            this.permission = permission;
            this.allowed = allowed;
        }
    }

    private final List<Entry> entries = new ArrayList<Entry>();
    private ACL parent;

    public SparseACL(ACL parent) {
        this.parent = parent;
    }

    public void add(Entry e) {
        entries.add(e);
    }

    public void add(Sid sid, Permission permission, boolean allowed) {
        add(new Entry(sid,permission,allowed));
    }

    public boolean hasPermission(Authentication a, Permission permission) {
        if(a==SYSTEM)   return true;
        Boolean b = _hasPermission(a,permission);
        if(b!=null) return b;

        if(parent!=null)
            return parent.hasPermission(a,permission);

        // the ultimate default is to reject everything
        return false;
    }

    @Override
    protected Boolean hasPermission(Sid p, Permission permission) {
        for( ; permission!=null; permission=permission.impliedBy ) {
            for (Entry e : entries) {
                if(e.permission==permission && e.sid.equals(p))
                    return e.allowed;
            }
        }
        return null;
    }
}
