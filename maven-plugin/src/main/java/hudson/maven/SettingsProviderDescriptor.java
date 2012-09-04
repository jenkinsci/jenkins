package hudson.maven;

import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import java.util.List;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class SettingsProviderDescriptor extends Descriptor<SettingsProvider> {


    public static List<SettingsProviderDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(SettingsProvider.class);
    }
}
