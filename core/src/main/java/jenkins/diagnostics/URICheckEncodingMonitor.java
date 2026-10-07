package jenkins.diagnostics;

import static hudson.Util.fixEmpty;

import hudson.Extension;
import hudson.Util;
import hudson.model.AdministrativeMonitor;
import hudson.util.FormValidation;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;

@Restricted(NoExternalUse.class)
@Extension
public class URICheckEncodingMonitor extends AdministrativeMonitor {

    private static final Logger LOGGER = Logger.getLogger(URICheckEncodingMonitor.class.getName());

    public boolean isCheckEnabled() {
        return !"ISO-8859-1".equalsIgnoreCase(Charset.defaultCharset().displayName());
    }

    @Override
    public boolean isActivated() {
        return true;
    }

    @Override
    public boolean isActivationFake() {
        return true;
    }

    @Override
    public String getDisplayName() {
        return Messages.URICheckEncodingMonitor_DisplayName();
    }

    public FormValidation doCheckURIEncoding(StaplerRequest2 request) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        // expected is non-ASCII String
        final String expected = "\u57f7\u4e8b";
        final String value = fixEmpty(request.getParameter("value"));

        if (!expected.equals(value)) {
            String expectedHex = Util.toHexString(expected.getBytes(StandardCharsets.UTF_8));
            String valueHex = value != null ? Util.toHexString(value.getBytes(StandardCharsets.UTF_8)) : null;
            LOGGER.log(Level.CONFIG, "Expected to receive: " + expected + " (" + expectedHex + ") but got: " + value + " (" + valueHex + ")");
            return FormValidation.warningWithMarkup(hudson.model.Messages.Hudson_NotUsesUTF8ToDecodeURL());
        }
        return FormValidation.ok();
    }
}
