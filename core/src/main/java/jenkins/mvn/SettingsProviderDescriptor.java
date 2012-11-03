package jenkins.mvn;

import hudson.model.Descriptor;

import java.util.List;

import jenkins.model.Jenkins;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class SettingsProviderDescriptor extends Descriptor<SettingsProvider> {


    public static List<SettingsProviderDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(SettingsProvider.class);
    }
}
