package hudson.security;

import hudson.model.Item;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Hudson;
import hudson.model.Run;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import net.sf.json.JSONObject;

import org.acegisecurity.Authentication;
import org.acegisecurity.acls.sid.GrantedAuthoritySid;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.acegisecurity.acls.sid.Sid;
import org.kohsuke.stapler.StaplerRequest;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/**
 * {@link JobProperty} to associate ACL for each project.
 */
public class AuthorizationMatrixProperty extends JobProperty<Job<?, ?>> {

	public static final JobPropertyDescriptor DESCRIPTOR = new DescriptorImpl();

	private transient SidACL acl = new AclImpl();

	private boolean useProjectSecurity;

	public boolean isUseProjectSecurity() {
		return useProjectSecurity;
	}

	public void setUseProjectSecurity(boolean useProjectSecurity) {
		this.useProjectSecurity = useProjectSecurity;
	}

	/**
	 * List up all permissions that are granted.
	 * 
	 * Strings are either the granted authority or the principal, which is not
	 * distinguished.
	 */
	private final Map<Permission, Set<String>> grantedPermissions = new HashMap<Permission, Set<String>>();

	private Set<String> sids = new HashSet<String>();

	public Set<String> getGroups() {
		return sids;
	}

	/**
	 * Returns all SIDs configured in this matrix, minus "anonymous"
	 * 
	 * @return Always non-null.
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

	/**
	 * Adds to {@link #grantedPermissions}. Use of this method should be limited
	 * during construction, as this object itself is considered immutable once
	 * populated.
	 */
	protected void add(Permission p, String sid) {
		Set<String> set = grantedPermissions.get(p);
		if (set == null)
			grantedPermissions.put(p, set = new HashSet<String>());
		set.add(sid);
		sids.add(sid);
	}

	@Override
	public JobPropertyDescriptor getDescriptor() {
		return DESCRIPTOR;
	}

	public static class DescriptorImpl extends JobPropertyDescriptor {

		@Override
		public JobProperty<?> newInstance(StaplerRequest req,
				JSONObject formData) throws FormException {
			boolean useProjectSecurity = formData.has("useProjectSecurity");
			AuthorizationMatrixProperty amp = new AuthorizationMatrixProperty();
			amp.setUseProjectSecurity(useProjectSecurity);
			for (Map.Entry<String, Object> r : (Set<Map.Entry<String, Object>>) formData
					.getJSONObject("data").entrySet()) {
				String sid = r.getKey();
				if (r.getValue() instanceof JSONObject) {
					for (Map.Entry<String, Boolean> e : (Set<Map.Entry<String, Boolean>>) ((JSONObject) r
							.getValue()).entrySet()) {
						if (e.getValue()) {
							Permission p = Permission.fromId(e.getKey());
							amp.add(p, sid);
						}
					}
				}
			}
			return amp;
		}

		protected DescriptorImpl() {
			super(AuthorizationMatrixProperty.class);
		}

		@Override
		public boolean isApplicable(Class<? extends Job> jobType) {
            // only applicable when ProjectMatrixAuthorizationStrategy is in charge
            return Hudson.getInstance().getAuthorizationStrategy() instanceof ProjectMatrixAuthorizationStrategy;
		}

		@Override
		public String getDisplayName() {
			return "Authorization Matrix";
		}

		public List<PermissionGroup> getAllGroups() {
			return Arrays.asList(PermissionGroup.get(Item.class),PermissionGroup.get(Run.class));
		}

        public boolean showPermission(Permission p) {
            return p!=Item.CREATE;
        }
	}

	private final class AclImpl extends SidACL {
		protected Boolean hasPermission(Sid sid, Permission p) {
			String s = toString(sid);
			for (; p != null; p = p.impliedBy) {
				Set<String> set = grantedPermissions.get(p);
				if (set != null && set.contains(s))
					return true;
			}
			return null;
		}

		protected Boolean _hasPermission(Authentication a, Permission permission) {
			Boolean b = super._hasPermission(a, permission);
			// permissions granted to anonymous users are granted to everyone
			if (b == null)
				b = hasPermission(ANONYMOUS, permission);
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

	private Object readResolve() {
		acl = new AclImpl();
		return this;
	}

	public SidACL getACL() {
		return acl;
	}

	/**
	 * Checks if the given SID has the given permission.
	 */
	public boolean hasPermission(String sid, Permission p) {
		for (; p != null; p = p.impliedBy) {
			Set<String> set = grantedPermissions.get(p);
			if (set != null && set.contains(sid))
				return true;
		}
		return false;
	}

	/**
	 * Works like {@link #add(Permission, String)} but takes both parameters
	 * from a single string of the form <tt>PERMISSIONID:sid</tt>
	 */
	private void add(String shortForm) {
		int idx = shortForm.indexOf(':');
		add(Permission.fromId(shortForm.substring(0, idx)), shortForm
				.substring(idx + 1));
	}

	/**
	 * Persist {@link ProjectMatrixAuthorizationStrategy} as a list of IDs that
	 * represent {@link ProjectMatrixAuthorizationStrategy#grantedPermissions}.
	 */
	public static final class ConverterImpl implements Converter {
		public boolean canConvert(Class type) {
			return type == AuthorizationMatrixProperty.class;
		}

		public void marshal(Object source, HierarchicalStreamWriter writer,
				MarshallingContext context) {
			AuthorizationMatrixProperty amp = (AuthorizationMatrixProperty) source;

			writer.startNode("useProjectSecurity");
			context.convertAnother(Boolean.valueOf(amp.isUseProjectSecurity()));
			writer.endNode();
			
			for (Entry<Permission, Set<String>> e : amp.grantedPermissions
					.entrySet()) {
				String p = e.getKey().getId();
				for (String sid : e.getValue()) {
					writer.startNode("permission");
					context.convertAnother(p + ':' + sid);
					writer.endNode();
				}
			}

		}

		public Object unmarshal(HierarchicalStreamReader reader,
				final UnmarshallingContext context) {
			AuthorizationMatrixProperty as = new AuthorizationMatrixProperty();

			String prop = reader.peekNextChild();
			if (prop!=null && prop.equals("useProjectSecurity")) {
				reader.moveDown();
				Boolean useSecurity = (Boolean) context.convertAnother(as, Boolean.class);
				as.setUseProjectSecurity(useSecurity.booleanValue());
				reader.moveUp();
			}
			while (reader.hasMoreChildren()) {
				reader.moveDown();
				String id = (String) context.convertAnother(as, String.class);
				as.add(id);
				reader.moveUp();
			}

			return as;
		}
	}
}
