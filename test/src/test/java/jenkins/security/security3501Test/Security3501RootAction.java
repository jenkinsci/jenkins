package jenkins.security.security3501Test;

import hudson.Util;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import java.io.IOException;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

@OptionalExtension
public class Security3501RootAction extends InvisibleAction implements RootAction {
    @Override
    public String getUrlName() {
        return "redirects";
    }

    public void doContent(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        final String path = req.getParameter("path");
        if (path != null && Util.isSafeToRedirectTo(path)) {
            rsp.sendRedirect2(path);
            return;
        }
        rsp.setStatus(404);
    }
}
