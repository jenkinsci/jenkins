package jenkins.diagnostics;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

import static hudson.Util.fixEmpty;

@Restricted(NoExternalUse.class)
@Extension
public class URICheckEncodingMonitor extends AdministrativeMonitor {

    public boolean isCheckEnabled() {
        return !"ISO-8859-1".equalsIgnoreCase(System.getProperty("file.encoding"));
    }

    @Override
    public boolean isActivated() {
        return true;
    }

    @Override
    public String getDisplayName() {
        return Messages.URICheckEncodingMonitor_DisplayName();
    }

    public FormValidation doCheckURIEncoding(StaplerRequest request) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        // expected is non-ASCII String
        final String expected = "\u57f7\u4e8b";
        final String value = fixEmpty(request.getParameter("value"));
        if (!expected.equals(value))
            return FormValidation.warningWithMarkup(hudson.model.Messages.Hudson_NotUsesUTF8ToDecodeURL());
        return FormValidation.ok();
    }
}
