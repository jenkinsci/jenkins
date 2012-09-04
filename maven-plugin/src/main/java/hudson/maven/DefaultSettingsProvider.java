package hudson.maven;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;

import static hudson.Util.fixEmpty;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DefaultSettingsProvider extends SettingsProvider {

    @DataBoundConstructor
    public DefaultSettingsProvider() {
    }

    @Override
    public void configure(ArgumentListBuilder margs, AbstractBuild project) throws IOException, InterruptedException {
        return;
    }

    @Extension(ordinal = 99)
    public static class DescriptorImpl extends SettingsProviderDescriptor {

        @Override
        public String getDisplayName() {
            return "Use default maven settings";
        }
    }
}
