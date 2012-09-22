package hudson.mvn;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.util.ArgumentListBuilder;
import hudson.util.IOUtils;

import java.io.IOException;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @author Dominik Bartholdi (imod)
 */
public class FilePathGlobalSettingsProvider extends GlobalSettingsProvider {

    private final String path;

    @DataBoundConstructor
    public FilePathGlobalSettingsProvider(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    @Override
    public void configure(ArgumentListBuilder margs, AbstractBuild project) throws IOException, InterruptedException {
        if (StringUtils.isEmpty(path)) return;
        if (IOUtils.isAbsolute(path)) {
            margs.add("-gs").add(path);
        } else {
            FilePath mrSettings = project.getModuleRoot().child(path);
            FilePath wsSettings = project.getWorkspace().child(path);
            if (!wsSettings.exists() && mrSettings.exists())
                wsSettings = mrSettings;

            margs.add("-gs").add(wsSettings.getRemote());
        }
    }


    @Extension(ordinal = 10)
    public static class DescriptorImpl extends SettingsProviderDescriptor {

        @Override
        public String getDisplayName() {
            return "File in project workspace";
        }
        
    }
}
