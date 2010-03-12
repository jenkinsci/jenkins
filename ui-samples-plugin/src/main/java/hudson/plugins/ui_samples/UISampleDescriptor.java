package hudson.plugins.ui_samples;

import hudson.model.Descriptor;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class UISampleDescriptor extends Descriptor<UISample> {
    @Override
    public String getDisplayName() {
        return clazz.getSimpleName();
    }
}
