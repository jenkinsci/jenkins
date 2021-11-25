package jenkins.formelementpath;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Main;
import hudson.model.PageDecorator;
import jenkins.util.SystemProperties;

@Extension
public class FormElementPathPageDecorator extends PageDecorator {

    @SuppressFBWarnings("MS_SHOULD_BE_FINAL")
    private static /*almost final */ boolean ENABLED = Main.isUnitTest ||
            SystemProperties.getBoolean(FormElementPathPageDecorator.class.getName() + ".enabled");

    public boolean isEnabled() {
        return ENABLED;
    }

}
