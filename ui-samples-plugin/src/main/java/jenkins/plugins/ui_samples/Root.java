package jenkins.plugins.ui_samples;

import hudson.Extension;
import hudson.model.RootAction;

import java.util.List;

/**
 * Entry point to all the UI samples.
 * 
 * @author Kohsuke Kawaguchi
 */
@Extension
public class Root implements RootAction {
    public String getIconFileName() {
        return "gear.gif";
    }

    public String getDisplayName() {
        return "UI Samples";
    }

    public String getUrlName() {
        return "ui-samples";
    }

    public UISample getDynamic(String name) {
        for (UISample ui : getAll())
            if (ui.getUrlName().equals(name))
                return ui;
        return null;
    }

    public List<UISample> getAll() {
        return UISample.all();
    }
}
