/*
 * The MIT License
 *
 * Copyright (c) 2022, CloudBees, Inc.
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
package hudson.model.userproperty;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.TransientUserActionFactory;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.security.UserDetailsCache;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.POST;

@Restricted(NoExternalUse.class)
public class UserPropertyCategoryAccountAction extends UserPropertyCategoryAction implements Action {
    public UserPropertyCategoryAccountAction(@NonNull User user) {
        super(user);
    }

    @Override
    public String getDisplayName() {
        return Messages.UserPropertyCategoryAccountAction_DisplayName();
    }

    @Override
    public String getIconFileName() {
        return getTargetUser().hasPermission(Jenkins.ADMINISTER) ? "symbol-settings" : null;
    }

    @Override
    public String getUrlName() {
        return "account";
    }

    public @NonNull List<UserPropertyDescriptor> getMyCategoryDescriptors() {
        return allByTwoCategoryClasses(UserPropertyCategory.Unclassified.class, UserPropertyCategory.Account.class);
    }

    private static List<UserPropertyDescriptor> allByTwoCategoryClasses(
            @NonNull Class<? extends UserPropertyCategory> categoryClass1,
            @NonNull Class<? extends UserPropertyCategory> categoryClass2
    ) {
        DescriptorExtensionList<UserProperty, UserPropertyDescriptor> all = UserProperty.all();

        List<UserPropertyDescriptor> filteredList = new ArrayList<>(all.size());
        for (UserPropertyDescriptor descriptor : all) {
            Class<? extends UserPropertyCategory> currClass = descriptor.getUserPropertyCategory().getClass();
            if (currClass.equals(categoryClass1) || currClass.equals(categoryClass2)) {
                filteredList.add(descriptor);
            }
        }

        return filteredList;
    }

    @POST
    public void doConfigSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, Descriptor.FormException {
        User targetUser = this.getTargetUser();
        targetUser.checkPermission(Jenkins.ADMINISTER);

        JSONObject json = req.getSubmittedForm();

        String oldFullName = targetUser.getFullName();
        targetUser.setFullName(json.getString("fullName"));
        targetUser.setDescription(json.getString("description"));

        super.doConfigSubmit(req, rsp);

        if (!oldFullName.equals(targetUser.getFullName())) {
            UserDetailsCache.get().invalidate(oldFullName);
        }
    }

    /**
     * Inject the outer class configuration page into the sidenav and the request routing of the user
     */
    @Extension(ordinal = 400)
    @Symbol("account")
    public static class AccountActionFactory extends TransientUserActionFactory {
        public Collection<? extends Action> createFor(User target) {
            return Collections.singleton(new UserPropertyCategoryAccountAction(target));
        }
    }
}
