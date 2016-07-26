package jenkins.management;

import hudson.Extension;
import hudson.model.ManagementLink;
import org.jenkinsci.Symbol;

@Extension(ordinal = Integer.MAX_VALUE - 800) @Symbol("editDescription")
public class EditDescriptionLink extends ManagementLink {

    @Override
    public String getIconFileName() {
        return "notepad.png";
    }

    public String getDisplayName() {
        return Messages.EditDescripionLink_DisplayName();
    }

    @Override
    public String getDescription() {
        return Messages.EditDescripionLink_Description();
    }

    @Override
    public String getUrlName() {
        return "/jenkins/?editDescription";
    }
}
