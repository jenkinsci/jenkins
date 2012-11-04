package jenkins.mvn;

import hudson.model.Descriptor;

import java.util.List;

import jenkins.model.Jenkins;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @author Dominik Bartholdi (imod)
 * @since 1.490
 */
public abstract class GlobalSettingsProviderDescriptor extends Descriptor<GlobalSettingsProvider> {


    public static List<GlobalSettingsProviderDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(GlobalSettingsProvider.class);
    }
}
