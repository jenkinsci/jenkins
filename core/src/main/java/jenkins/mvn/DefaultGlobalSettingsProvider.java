package jenkins.mvn;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @author Dominik Bartholdi (imod)
 * @since 1.491
 */
public class DefaultGlobalSettingsProvider extends GlobalSettingsProvider {

    @DataBoundConstructor
    public DefaultGlobalSettingsProvider() {
    }

    @Override
    public FilePath supplySettings(AbstractBuild<?, ?> project, TaskListener listener) {
        return null;
    }

    @Extension(ordinal = 99) @Symbol("standard")
    public static class DescriptorImpl extends GlobalSettingsProviderDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.DefaultGlobalSettingsProvider_DisplayName();
        }
    }
}
