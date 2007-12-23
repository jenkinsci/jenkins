package hudson.security;

import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.security.Permission.Group;
import net.sf.json.JSONObject;
import org.acegisecurity.acls.sid.Sid;
import org.acegisecurity.acls.sid.GrantedAuthoritySid;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map.Entry;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Role-based authorization via a matrix.
 *
 * @author Kohsuke Kawaguchi
 */
public class GlobalMatrixAuthorizationStrategy extends AuthorizationStrategy {
    private transient ACL acl = new AclImpl();

    /**
     * List up all permissions that are granted.
     *
     * Strings are either the granted authority or the principal,
     * which is not distinguished.
     */
    private final Map<Permission,Set<String>> grantedPermissions = new HashMap<Permission, Set<String>>();

    @Override
    public ACL getRootACL() {
        return acl;
    }

    private Object readResolve() {
        acl = new AclImpl();
        return this;
    }

    private final class AclImpl extends SidACL {
        protected Boolean hasPermission(Sid p, Permission permission) {
            Set<String> set = grantedPermissions.get(permission);
            if(set==null)       return null;
            if(set.contains(toString(p)))   return true;
            return null;
        }

        private String toString(Sid p) {
            if (p instanceof GrantedAuthoritySid)
                return ((GrantedAuthoritySid) p).getGrantedAuthority();
            if (p instanceof PrincipalSid)
                return ((PrincipalSid) p).getPrincipal();
            // hmm...
            return p.toString();
        }
    }

    public Descriptor<AuthorizationStrategy> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<AuthorizationStrategy> DESCRIPTOR = new DescriptorImpl();

    /**
     * Persist {@link GlobalMatrixAuthorizationStrategy} as a list of IDs that
     * represent {@link GlobalMatrixAuthorizationStrategy#grantedPermissions}.
     */
    public static final class ConverterImpl implements Converter {
        private final Converter collectionConv; // used to convert ArrayList in it

        public ConverterImpl(Converter collectionConv) {
            this.collectionConv = collectionConv;
        }

        public boolean canConvert(Class type) {
            return type== GlobalMatrixAuthorizationStrategy.class;
        }

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            GlobalMatrixAuthorizationStrategy strategy = (GlobalMatrixAuthorizationStrategy)source;

            List<String> permissions = new ArrayList<String>();
            for (Entry<Permission, Set<String>> e : strategy.grantedPermissions.entrySet()) {
                String p = e.getKey().getId();
                for (String sid : e.getValue())
                    permissions.add(p+':'+sid);
            }

            collectionConv.marshal( permissions, writer, context );
        }

        public Object unmarshal(HierarchicalStreamReader reader, final UnmarshallingContext context) {
            GlobalMatrixAuthorizationStrategy as = new GlobalMatrixAuthorizationStrategy();

            for( String id : (List<String>)(collectionConv.unmarshal(reader,context))) {
                int idx = id.indexOf(':');
                Permission p = Permission.fromId(id.substring(0,idx));
                Set<String> set = as.grantedPermissions.get(p);
                if(set==null)
                    as.grantedPermissions.put(p,set = new HashSet<String>());
                set.add(id.substring(idx+1));
            }

            return as;
        }
    }
    
    static {
        LIST.add(DESCRIPTOR);
        Hudson.XSTREAM.registerConverter(
            new GlobalMatrixAuthorizationStrategy.ConverterImpl(
                new CollectionConverter(Hudson.XSTREAM.getClassMapper())
            ),10);
    }

    public static final class DescriptorImpl extends Descriptor<AuthorizationStrategy> {
        public DescriptorImpl() {
            super(GlobalMatrixAuthorizationStrategy.class);
        }

        public String getDisplayName() {
            return "Role-based security";
        }

        public AuthorizationStrategy newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            // TODO: configure
            return new GlobalMatrixAuthorizationStrategy();
        }

        public String getHelpFile() {
            return "/help/security/full-control-once-logged-in.html";
        }

        public List<Group> getAllGroups() {
            return Permission.getAllGroups();
        }
    }
}

