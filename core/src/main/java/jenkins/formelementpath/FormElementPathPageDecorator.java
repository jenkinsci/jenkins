package jenkins.formelementpath;

import hudson.Extension;
import hudson.model.PageDecorator;
import jenkins.util.SystemProperties;

@Extension
public class FormElementPathPageDecorator extends PageDecorator {

    private static /*almost final */ boolean ENABLED =
            SystemProperties.getBoolean(FormElementPathPageDecorator.class.getName() + ".enabled");

    public boolean isEnabled() {
        return ENABLED;
    }

}
