package hudson.mvn;

import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import java.util.List;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @author Dominik Bartholdi (imod)
 */
public abstract class GlobalSettingsProviderDescriptor extends Descriptor<GlobalSettingsProvider> {


    public static List<GlobalSettingsProviderDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(GlobalSettingsProvider.class);
    }
}
