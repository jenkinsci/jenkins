package jenkins.mvn;

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;

import java.util.List;

import jenkins.model.Jenkins;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @author Dominik Bartholdi (imod)
 * @since 1.491
 */
public abstract class GlobalSettingsProviderDescriptor extends Descriptor<GlobalSettingsProvider> {

    @WithBridgeMethods(List.class)
    public static DescriptorExtensionList<GlobalSettingsProvider,GlobalSettingsProviderDescriptor> all() {
        return Jenkins.getInstance().<GlobalSettingsProvider,GlobalSettingsProviderDescriptor>getDescriptorList(GlobalSettingsProvider.class);
    }
}
