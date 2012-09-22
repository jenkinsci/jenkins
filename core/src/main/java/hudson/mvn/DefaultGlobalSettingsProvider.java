package hudson.mvn;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.util.ArgumentListBuilder;

import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @author Dominik Bartholdi (imod)
 */
public class DefaultGlobalSettingsProvider extends GlobalSettingsProvider {

    @DataBoundConstructor
    public DefaultGlobalSettingsProvider() {
    }

    @Override
    public void configure(ArgumentListBuilder margs, AbstractBuild project) throws IOException, InterruptedException {
        return;
    }

    @Extension(ordinal = 99)
    public static class DescriptorImpl extends GlobalSettingsProviderDescriptor {

        @Override
        public String getDisplayName() {
            return "Use default maven global settings";
        }
    }
}
