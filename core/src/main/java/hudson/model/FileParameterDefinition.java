package hudson.model;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * {@link ParameterDefinition} for doing file upload.
 *
 * <p>
 * The file will be then placed into the workspace at the beginning of a build.
 *
 * @author Kohsuke Kawaguchi
 */
public class FileParameterDefinition extends ParameterDefinition {
    @DataBoundConstructor
    public FileParameterDefinition(String name) {
        super(name);
    }

    public ParameterDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public FileParameterValue createValue(StaplerRequest req, JSONObject jo) {
        FileParameterValue p = req.bindJSON(FileParameterValue.class, jo);
        p.setLocation(getName());
        return p;
    }

    public static final ParameterDescriptor DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends ParameterDescriptor {

        protected DescriptorImpl() {
            super(FileParameterDefinition.class);
        }

        @Override
        public String getDisplayName() {
            return Messages.FileParameterDefinition_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/parameter/file.html";
        }
    }
}
