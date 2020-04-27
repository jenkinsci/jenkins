package jenkins.model;

import hudson.Extension;
import hudson.RestrictedSince;
import hudson.model.Descriptor;
import hudson.model.RootAction;
import hudson.slaves.Cloud;
import hudson.util.FormApply;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.POST;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Provides a configuration form for {@link Jenkins#clouds}.
 *
 * Has been overhauled in Jenkins 2.XXX to no longer contribute to Configure System, but be a standalone form.
 */
@Extension
@Symbol("cloud")
@Restricted(NoExternalUse.class)
@RestrictedSince("TODO")
public class GlobalCloudConfiguration implements RootAction {

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return Messages.GlobalCloudConfiguration_DisplayName();
    }

    @Override
    public String getUrlName() {
        return "configureClouds";
    }

    @POST
    public void doConfigure(StaplerRequest req, StaplerResponse rsp) throws Descriptor.FormException, IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        JSONObject json = req.getSubmittedForm();
        Jenkins.get().clouds.rebuildHetero(req,json, Cloud.all(), "cloud");
        FormApply.success(req.getContextPath() + "/manage").generateResponse(req, rsp, null);
    }
}
