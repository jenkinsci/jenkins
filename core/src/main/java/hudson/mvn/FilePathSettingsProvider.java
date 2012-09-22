package hudson.mvn;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.IOUtils;

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class FilePathSettingsProvider extends SettingsProvider {

    private final String path;

    @DataBoundConstructor
    public FilePathSettingsProvider(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    @Override
    public void configure(ArgumentListBuilder margs, AbstractBuild project) throws IOException, InterruptedException {
        if (StringUtils.isEmpty(path)) return;
        if (IOUtils.isAbsolute(path)) {
            margs.add("-s").add(path);
        } else {
            FilePath mrSettings = project.getModuleRoot().child(path);
            FilePath wsSettings = project.getWorkspace().child(path);
            if (!wsSettings.exists() && mrSettings.exists())
                wsSettings = mrSettings;

            margs.add("-s").add(wsSettings.getRemote());
        }
    }


    @Extension(ordinal = 10)
    public static class DescriptorImpl extends SettingsProviderDescriptor {

        /**
         * Check that the provided file is a relative path. And check that it exists, just in case.
         */
        public FormValidation doCheck(@AncestorInPath AbstractProject job, @QueryParameter String value) throws IOException, ServletException {
        	
        	// TODO why not support an absolute path?
        	
//            String v = fixEmpty(value);
//            if ((v == null) || (v.length() == 0)) {
//                // Null values are allowed.
//                return FormValidation.ok();
//            }
//            if ((v.startsWith("/")) || (v.startsWith("\\")) || (v.matches("^\\w\\:\\\\.*"))) {
//                return FormValidation.error(Messages.MavenModuleSet_AlternateSettingsRelativePath());
//            }
//
//            Run lb = job.getLastBuild();
//            if (lb!=null) {
//                FilePath ws = lb.getWorkspace();
//                if(ws!=null)
//                    return ws.validateRelativePath(value,true,true);
//            }
            return FormValidation.ok();
        }

        @Override
        public String getDisplayName() {
            return "File in project workspace";
        }
    }
}
