package hudson.security;

import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.View;
import hudson.util.FormFieldValidator;

import java.util.Collections;
import java.util.List;

import net.sf.json.JSONObject;

import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.acegisecurity.acls.sid.GrantedAuthoritySid;
import org.kohsuke.stapler.StaplerRequest;

/**
 * {@link AuthorizationStrategy} that grants control based on owned permissions.
 */
// TODO: ask Tom to explain what this class is.
public class ProjectBasedAuthorizationStrategy extends AuthorizationStrategy {
    @Override
    public ACL getRootACL() {
        return ROOT_ACL;
    }

    @Override
	public ACL getACL(AbstractProject<?, ?> project) {
    	SparseACL acl = new SparseACL(ROOT_ACL);
    	acl.add(new GrantedAuthoritySid(createItemAuthority(project)), Permission.FULL_CONTROL, true);
    	return acl;
	}

    @Override
	public ACL getACL(Computer computer) {
    	SparseACL acl = new SparseACL(ROOT_ACL);
    	acl.add(ACL.ANONYMOUS, Hudson.ADMINISTER, false);
    	acl.add(ACL.EVERYONE, Hudson.ADMINISTER, true);
    	return acl;
	}

	@Override
	public ACL getACL(View view) {
    	SparseACL acl = new SparseACL(ROOT_ACL);
    	acl.add(ACL.ANONYMOUS, Permission.CREATE, false);
    	acl.add(ACL.EVERYONE, Permission.CREATE, true);
    	return acl;
	}

    public List<String> getGroups() {
        return Collections.emptyList();
    }

    public static final SparseACL ROOT_ACL = new SparseACL(null);

    static {
    	ROOT_ACL.add(new GrantedAuthoritySid("admin"), Permission.FULL_CONTROL, true);
    	ROOT_ACL.add(ACL.ANONYMOUS,FormFieldValidator.CHECK,false);
        ROOT_ACL.add(ACL.EVERYONE,FormFieldValidator.CHECK,true);
        ROOT_ACL.add(ACL.ANONYMOUS, Permission.CREATE, false);
        ROOT_ACL.add(ACL.EVERYONE, Permission.CREATE, true);
        ROOT_ACL.add(ACL.EVERYONE,Permission.READ,true);
        ROOT_ACL.add(ACL.EVERYONE,Permission.FULL_CONTROL,false);
    }
    
    public GrantedAuthority createItemAuthority(Item item) {
    	return new GrantedAuthorityImpl(item.getFullName());
    }

    public Descriptor<AuthorizationStrategy> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<AuthorizationStrategy> DESCRIPTOR = new Descriptor<AuthorizationStrategy>(ProjectBasedAuthorizationStrategy.class) {
        public String getDisplayName() {
            return "Project Based Access Control";
        }

        public AuthorizationStrategy newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new ProjectBasedAuthorizationStrategy();
        }

        public String getHelpFile() {
            return "/help/security/full-control-once-logged-in.html";
        }
    };
}
