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
 * {@link GlobalMatrixAuthorizationStrategy} plus per-project ACL.
 *
 * @author Kohsuke Kawaguchi
 */
public class ProjectMatrixAuthorizationStrategy extends GlobalMatrixAuthorizationStrategy {
    @Override
    public ACL getACL(AbstractProject<?,?> project) {
        AuthorizationMatrixProperty amp = project.getProperty(AuthorizationMatrixProperty.class);
        if (amp != null && amp.isUseProjectSecurity()) {
            return amp.getACL();
        } else {
            return getRootACL();
        }
    }

    public Descriptor<AuthorizationStrategy> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<AuthorizationStrategy> DESCRIPTOR = new DescriptorImpl(ProjectMatrixAuthorizationStrategy.class) {
        @Override
        protected GlobalMatrixAuthorizationStrategy create() {
            return new ProjectMatrixAuthorizationStrategy();
        }

        @Override
        public String getDisplayName() {
            return Messages.ProjectMatrixAuthorizationStrategy_DisplayName();
        }
    };

    static {
        LIST.add(DESCRIPTOR);
        Jobs.PROPERTIES.add(AuthorizationMatrixProperty.DESCRIPTOR);
    }
}

