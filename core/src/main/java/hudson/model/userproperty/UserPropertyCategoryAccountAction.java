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
import hudson.util.FormApply;
import jenkins.model.Jenkins;
import jenkins.security.UserDetailsCache;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.POST;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Restricted(NoExternalUse.class)
public class UserPropertyCategoryAccountAction implements Action {
    private final @NonNull User targetUser;

    public UserPropertyCategoryAccountAction(@NonNull User user) {
        this.targetUser = user;
    }

    @SuppressWarnings("unused") // Jelly use
    public @NonNull User getTargetUser() {
        return targetUser;
    }

    @Override
    public String getDisplayName() {
        return Messages.UserPropertyCategoryAccountAction_DisplayName();
    }

    @Override
    public String getIconFileName() {
        return "symbol-settings";
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
    public void doAccountConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        this.targetUser.checkPermission(Jenkins.ADMINISTER);

        JSONObject json = req.getSubmittedForm();

        String oldFullName = this.targetUser.getFullName();
        this.targetUser.setFullName(json.getString("fullName"));
        this.targetUser.setDescription(json.getString("description"));

        List<UserProperty> props = new ArrayList<>();
        List<UserPropertyDescriptor> myCategoryDescriptors = getMyCategoryDescriptors();
        int i = 0;
        for (UserPropertyDescriptor d : myCategoryDescriptors) {
            UserProperty p = this.targetUser.getProperty(d.clazz);

            JSONObject o = json.optJSONObject("userProperty" + i++);
            if (o != null) {
                if (p != null) {
                    p = p.reconfigure(req, o);
                } else {
                    p = d.newInstance(req, o);
                }
            }

            if (p != null) {
                props.add(p);
            }
        }
        this.targetUser.addProperties(props);

        this.targetUser.save();

        if (oldFullName != null && !oldFullName.equals(this.targetUser.getFullName())) {
            UserDetailsCache.get().invalidate(oldFullName);
        }
        
        // we are in /user/<userLogin>/account/, going to /user/<userLogin>/
        FormApply.success("..").generateResponse(req, rsp, this);
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
