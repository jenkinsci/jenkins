package hudson.security;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.Jobs;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.acls.sid.GrantedAuthoritySid;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.acegisecurity.acls.sid.Sid;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Role-based authorization via a matrix.
 *
 * @author Kohsuke Kawaguchi
 */
public class ProjectMatrixAuthorizationStrategy extends AuthorizationStrategy {
    protected transient ACL acl = new AclImpl();

    /**
     * List up all permissions that are granted.
     *
     * Strings are either the granted authority or the principal,
     * which is not distinguished.
     */
    private final Map<Permission,Set<String>> grantedPermissions = new HashMap<Permission, Set<String>>();

    private final Set<String> sids = new HashSet<String>();

    /**
     * Adds to {@link #grantedPermissions}.
     * Use of this method should be limited during construction,
     * as this object itself is considered immutable once populated.
     */
    protected void add(Permission p, String sid) {
        Set<String> set = grantedPermissions.get(p);
        if(set==null)
            grantedPermissions.put(p,set = new HashSet<String>());
        set.add(sid);
        sids.add(sid);
    }

	/**
     * Works like {@link #add(Permission, String)} but takes both parameters
     * from a single string of the form <tt>PERMISSIONID:sid</tt>
     */
    private void add(String shortForm) {
        int idx = shortForm.indexOf(':');
        add(Permission.fromId(shortForm.substring(0,idx)),shortForm.substring(idx+1));
    }

    @Override
    public ACL getRootACL() {
        return acl;
    }

    @Override
	public ACL getACL(AbstractProject<?, ?> project) {
    	AuthorizationMatrixProperty amp = project.getProperty(AuthorizationMatrixProperty.class);
    	if (amp != null && amp.isUseProjectSecurity()) {
    		return amp.getACL();
    	} else {
    		return getRootACL();
    	}
	}

    public Set<String> getGroups() {
        return sids;
    }

    private Object readResolve() {
        acl = new AclImpl();
        return this;
    }

    /**
     * Checks if the given SID has the given permission.
     */
    public boolean hasPermission(String sid, Permission p) {
        for(; p!=null; p=p.impliedBy) {
            Set<String> set = grantedPermissions.get(p);
            if(set!=null && set.contains(sid))
                return true;
        }
        return false;
    }

    /**
     * Returns all SIDs configured in this matrix, minus "anonymous"
     *
     * @return
     *      Always non-null.
     */
    public List<String> getAllSIDs() {
        Set<String> r = new HashSet<String>();
        for (Set<String> set : grantedPermissions.values())
            r.addAll(set);
        r.remove("anonymous");

        String[] data = r.toArray(new String[r.size()]);
        Arrays.sort(data);
        return Arrays.asList(data);
    }

    protected final class AclImpl extends SidACL {
        protected Boolean hasPermission(Sid p, Permission permission) {
            if(ProjectMatrixAuthorizationStrategy.this.hasPermission(toString(p),permission))
                return true;
            return null;
        }

        protected Boolean _hasPermission(Authentication a, Permission permission) {
            Boolean b = super._hasPermission(a,permission);
            // permissions granted to anonymous users are granted to everyone
            if(b==null) b=hasPermission(ANONYMOUS,permission);
            return b;
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
     * Persist {@link ProjectMatrixAuthorizationStrategy} as a list of IDs that
     * represent {@link ProjectMatrixAuthorizationStrategy#grantedPermissions}.
     */
    public static final class ConverterImpl implements Converter {
        public boolean canConvert(Class type) {
            return type== ProjectMatrixAuthorizationStrategy.class;
        }

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            ProjectMatrixAuthorizationStrategy strategy = (ProjectMatrixAuthorizationStrategy)source;

            for (Entry<Permission, Set<String>> e : strategy.grantedPermissions.entrySet()) {
                String p = e.getKey().getId();
                for (String sid : e.getValue()) {
                    writer.startNode("permission");
                    context.convertAnother(p+':'+sid);
                    writer.endNode();
                }
            }

        }

        public Object unmarshal(HierarchicalStreamReader reader, final UnmarshallingContext context) {
            ProjectMatrixAuthorizationStrategy as = new ProjectMatrixAuthorizationStrategy();

            while (reader.hasMoreChildren()) {
                reader.moveDown();
                String id = (String)context.convertAnother(as,String.class);
                as.add(id);
                reader.moveUp();
            }

            return as;
        }
    }
    
    static {
        LIST.add(DESCRIPTOR);
        Jobs.PROPERTIES.add(AuthorizationMatrixProperty.DESCRIPTOR);
    }

    public static final class DescriptorImpl extends Descriptor<AuthorizationStrategy> {
        public DescriptorImpl() {
            super(ProjectMatrixAuthorizationStrategy.class);
        }

        public String getDisplayName() {
            return Messages.ProjectMatrixAuthorizationStrategy_DisplayName();
        }

        public AuthorizationStrategy newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            ProjectMatrixAuthorizationStrategy gmas = new ProjectMatrixAuthorizationStrategy();
            for(Map.Entry<String,JSONObject> r : (Set<Map.Entry<String,JSONObject>>)formData.getJSONObject("data").entrySet()) {
                String sid = r.getKey();
                for(Map.Entry<String,Boolean> e : (Set<Map.Entry<String,Boolean>>)r.getValue().entrySet()) {
                    if(e.getValue()) {
                        Permission p = Permission.fromId(e.getKey());
                        gmas.add(p,sid);
                    }
                }
            }
            return gmas;
        }

        public String getHelpFile() {
            return "/help/security/global-matrix.html";
        }

        public List<PermissionGroup> getAllGroups() {
            List<PermissionGroup> groups = new ArrayList<PermissionGroup>(PermissionGroup.getAll());
            groups.remove(PermissionGroup.get(Permission.class));
            return groups;
        }
    }

}

