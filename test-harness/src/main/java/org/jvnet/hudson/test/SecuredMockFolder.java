/*
 * The MIT License
 *
 * Copyright (c) 2015 Oleg Nenashev.
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
package org.jvnet.hudson.test;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.security.SidACL;
import hudson.security.SparseACL;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.acls.sid.Sid;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Folder stub with a configurable permission control.
 * The implementation secures the access to the item and to its children.
 * @author Oleg Nenashev
 * @since TODO
 */
@Restricted(NoExternalUse.class) // Unrestrict after integrating into Jenkins trunk
public class SecuredMockFolder extends MockFolder {

    private String grantedUser;
    private Set<String> grantedPermissions;

    private SecuredMockFolder(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override
    public TopLevelItem getItem(String name) {
        final TopLevelItem item = super.getItem(name);
        if (item != null && item.hasPermission(Item.READ)) {
            return item;
        }
        return null;
    }

    @Override
    public boolean hasPermission(Permission p) {
        if (super.hasPermission(p)) {
            return true;
        }
        return hasPermissionInField(Jenkins.getAuthentication().getName(), p);
    }
    
    private boolean hasPermissionInField(String sid, @Nonnull Permission p) {
        if (sid.equals(grantedUser)) {
            if (grantedPermissions != null && grantedPermissions.contains(p.getId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ACL getACL() {
        return new ACLWrapper();
    }
    
    public void setPermissions(String username, Permission... permissions) {
        this.grantedUser = username;
        if (grantedPermissions == null) {
            grantedPermissions = new HashSet<String>();
        } else {
            grantedPermissions.clear();
        }
        for (Permission p : permissions) {
            grantedPermissions.add(p.getId());
        }
    }

    @Extension
    public static class DescriptorImpl extends TopLevelItemDescriptor {

        @Override
        public String getDisplayName() {
            return "MockFolder with security control";
        }

        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new SecuredMockFolder(parent, name);
        }
    }
    
    private class ACLWrapper extends SidACL {

        @Override
        protected Boolean hasPermission(Sid p, Permission permission) {
            //TODO: Handle globally defined permissions?
            return SecuredMockFolder.this.hasPermissionInField(toString(p), permission);
        }
    }
}
