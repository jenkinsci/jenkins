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

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.VersionNumber;
import hudson.util.FormValidation.Kind;
import hudson.Functions;
import hudson.Extension;
import net.sf.json.JSONObject;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.acegisecurity.acls.sid.Sid;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import org.springframework.dao.DataAccessException;

import javax.servlet.ServletException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.io.IOException;
import java.io.Serializable;

/**
 * Role-based authorization via a matrix.
 *
 * @author Kohsuke Kawaguchi
 */
// TODO: think about the concurrency commitment of this class
public class GlobalMatrixAuthorizationStrategy extends AuthorizationStrategy {
    private transient SidACL acl = new AclImpl();

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
    public void add(Permission p, String sid) {
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
    public SidACL getRootACL() {
        return acl;
    }

    public Set<String> getGroups() {
        return sids;
    }

    /**
     * In earlier version of Hudson we used to use reflection converter, which calls this method.
     * This is now unmarshaller via {@link ConverterImpl}
     */
    private Object readResolve() {
        migrateHudson2324(grantedPermissions);
        acl = new AclImpl();
        return this;
    }

    /**
     * Due to HUDSON-2324, we want to inject Item.READ permission to everyone who has Hudson.READ,
     * to remain backward compatible
     * @param grantedPermissions
     */
    /*package*/ static void migrateHudson2324(Map<Permission,Set<String>> grantedPermissions) {
        if(Hudson.getInstance().isUpgradedFromBefore(new VersionNumber("1.300.*"))) {
            Set<String> f = grantedPermissions.get(Hudson.READ);
            if(f!=null) {
                Set<String> t = grantedPermissions.get(Item.READ);
                if(t!=null) t.addAll(f);
                else        t=new HashSet<String>(f);
                grantedPermissions.put(Item.READ,t);
            }
        }
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
     * Checks if the permission is explicitly given, instead of implied through {@link Permission#impliedBy}.
     */
    public boolean hasExplicitPermission(String sid, Permission p) {
        Set<String> set = grantedPermissions.get(p);
        return set != null && set.contains(sid);
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

    private final class AclImpl extends SidACL {
        protected Boolean hasPermission(Sid p, Permission permission) {
            if(GlobalMatrixAuthorizationStrategy.this.hasPermission(toString(p),permission))
                return true;
            return null;
        }
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    /**
     * Persist {@link GlobalMatrixAuthorizationStrategy} as a list of IDs that
     * represent {@link GlobalMatrixAuthorizationStrategy#grantedPermissions}.
     */
    public static class ConverterImpl implements Converter {
        public boolean canConvert(Class type) {
            return type==GlobalMatrixAuthorizationStrategy.class;
        }

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            GlobalMatrixAuthorizationStrategy strategy = (GlobalMatrixAuthorizationStrategy)source;

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
            GlobalMatrixAuthorizationStrategy as = create();

            while (reader.hasMoreChildren()) {
                reader.moveDown();
                String id = (String)context.convertAnother(as,String.class);
                as.add(id);
                reader.moveUp();
            }

            migrateHudson2324(as.grantedPermissions);

            return as;
        }

        protected GlobalMatrixAuthorizationStrategy create() {
            return new GlobalMatrixAuthorizationStrategy();
        }
    }
    
    public static class DescriptorImpl extends Descriptor<AuthorizationStrategy> {
        protected DescriptorImpl(Class<? extends GlobalMatrixAuthorizationStrategy> clazz) {
            super(clazz);
        }

        public DescriptorImpl() {
        }

        public String getDisplayName() {
            return Messages.GlobalMatrixAuthorizationStrategy_DisplayName();
        }

        public AuthorizationStrategy newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            GlobalMatrixAuthorizationStrategy gmas = create();
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

        protected GlobalMatrixAuthorizationStrategy create() {
            return new GlobalMatrixAuthorizationStrategy();
        }

        public String getHelpFile() {
            return "/help/security/global-matrix.html";
        }

        public List<PermissionGroup> getAllGroups() {
            List<PermissionGroup> groups = new ArrayList<PermissionGroup>(PermissionGroup.getAll());
            groups.remove(PermissionGroup.get(Permission.class));
            return groups;
        }

        public boolean showPermission(Permission p) {
            return true;
        }

        public FormValidation doCheckName(@QueryParameter String value ) throws IOException, ServletException {
            return doCheckName(value, Hudson.getInstance(), Hudson.ADMINISTER);
        }

        FormValidation doCheckName(String value, AccessControlled subject, Permission permission) throws IOException, ServletException {
            if(!subject.hasPermission(permission))  return FormValidation.ok(); // can't check

            final String v = value.substring(1,value.length()-1);
            SecurityRealm sr = Hudson.getInstance().getSecurityRealm();
            String ev = Functions.escape(v);

            if(v.equals("authenticated"))
                // systerm reserved group
                return FormValidation.respond(Kind.OK, makeImg("user.gif") +ev);

            try {
                sr.loadUserByUsername(v);
                return FormValidation.respond(Kind.OK, makeImg("person.gif")+ev);
            } catch (UserMayOrMayNotExistException e) {
                // undecidable, meaning the user may exist
                return FormValidation.respond(Kind.OK, ev);
            } catch (UsernameNotFoundException e) {
                // fall through next
            } catch (DataAccessException e) {
                // fall through next
            }

            try {
                sr.loadGroupByGroupname(v);
                return FormValidation.respond(Kind.OK, makeImg("user.gif") +ev);
            } catch (UserMayOrMayNotExistException e) {
                // undecidable, meaning the group may exist
                return FormValidation.respond(Kind.OK, ev);
            } catch (UsernameNotFoundException e) {
                // fall through next
            } catch (DataAccessException e) {
                // fall through next
            }

            // couldn't find it. it doesn't exit
            return FormValidation.respond(Kind.ERROR, makeImg("error.gif") +ev);
        }

        private String makeImg(String gif) {
            return String.format("<img src='%s%s/images/16x16/%s' style='margin-right:0.2em'>", Stapler.getCurrentRequest().getContextPath(), Hudson.RESOURCE_PATH, gif);
        }
    }
}

