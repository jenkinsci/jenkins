package hudson.model;

import org.kohsuke.stapler.DataBoundConstructor;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItem;
import hudson.tasks.BuildWrapper;
import hudson.Launcher;

import java.io.IOException;

/**
 * {@link ParameterValue} for {@link FileParameterDefinition}.
 *
 * <h2>Persistence</h2>
 * <p>
 * {@link DiskFileItem} is persistable via serialization,
 * (although the data may get very large in XML) so this object
 * as a whole is persistable.
 *
 * @author Kohsuke Kawaguchi
 */
public class FileParameterValue extends ParameterValue {
    private FileItem file;

    private String location;

    @DataBoundConstructor
    public FileParameterValue(String name, FileItem file) {
        super(name);
        assert file!=null;
        this.file = file;
    }

    // post initialization hook
    /*package*/ void setLocation(String location) {
        this.location = location;
    }

    public BuildWrapper createBuildWrapper(AbstractBuild<?,?> build) {
        return new BuildWrapper() {
            public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
                listener.getLogger().println("Copying file to "+location);
                build.getProject().getWorkspace().child(location).copyFrom(file);
                file = null;
                return new Environment() {};
            }

            public Descriptor<BuildWrapper> getDescriptor() {
                return null;
            }
        };
    }
}
