package hudson.plugins.ui_samples;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Action;
import hudson.model.Describable;
import hudson.model.Hudson;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class UISample implements ExtensionPoint, Action, Describable<UISample> {
    public String getIconFileName() {
        return "gear.gif";
    }

    public String getUrlName() {
        return getClass().getSimpleName();
    }

    /**
     * Default display name.
     */
    public String getDisplayName() {
        return getClass().getSimpleName();
    }

    /**
     * Returns a paragraph of natural text that describes this sample.
     * Interpreted as HTML.
     */
    public abstract String getDescription();

    public UISampleDescriptor getDescriptor() {
        return (UISampleDescriptor)Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Returns all the registered {@link UISample}s.
     */
    public static ExtensionList<UISample> all() {
        return Hudson.getInstance().getExtensionList(UISample.class);
    }

}
