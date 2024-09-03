package hudson.model.userproperty;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Descriptor;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.util.FormApply;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.POST;

public abstract class UserPropertyCategoryAction {

    private final User targetUser;

    public UserPropertyCategoryAction(User targetUser) {
        this.targetUser = targetUser;
    }

    public @NonNull User getTargetUser() {
        return targetUser;
    }

    public @NonNull abstract List<UserPropertyDescriptor> getMyCategoryDescriptors();

    @POST
    public void doConfigSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, Descriptor.FormException {
        this.targetUser.checkPermission(Jenkins.ADMINISTER);

        JSONObject json = req.getSubmittedForm();

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

        // we are in /user/<userLogin>/<category>/, going to /user/<userLogin>/
        FormApply.success("..").generateResponse(req, rsp, this);
    }
}
