package jenkins.data;

import hudson.Extension;
import hudson.cli.CLICommand;
import jenkins.data.tree.Mapping;
import jenkins.data.tree.Scalar;
import jenkins.data.tree.TreeNode;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;

@Extension
public class SampleDataCLI extends CLICommand {

    private boolean read = true;

    @Override
    public String getShortDescription() {
        return "Provisioning";
    }

    @Override
    protected int run() throws Exception {
        if (read) {
            VersionedEnvelope<Provisioning> envelope = new JsonSerializer().read(Provisioning.class, stdin);
            for (Provisioning res : envelope.getData()) {
                System.out.println(res);
            }
            return 0;
        } else {
            VersionedEnvelope<Provisioning> data = new VersionedEnvelope<>(1, Arrays.asList(
                    new Provisioning("3"),
                    new Provisioning("4")
            ));
            new JsonSerializer().write(data, stdout);
            return 0;
        }
    }

    public static final class JsonSerializer extends Serializer {

        @Override
        protected TreeNode unstring(Reader in) throws IOException {
            // let's pretend some parsing and mapping mechanism runs here
            // jackson could be plugged here

            Mapping mapping = new Mapping();
            mapping.put("memoryGB", new Scalar(4));
            return mapping;
        }

        @Override
        protected void stringify(TreeNode tree, Writer out) throws IOException {
            // jackson serialization of tree could be plugged here
            out.write("{}");
        }
    }

    // Antonio: Ideally this class should not require to know about the existence of its APIResource
    // ProvisioningResource should be able to register itself as the APIResource for this
    public static final class Provisioning implements APIExportable<ProvisioningResource> {

        private String memory;

        public Provisioning(String memory) {
            this.memory = memory;
        }

        public String getMemory() {
            return memory;
        }

        public void setMemory(String memory) {
            this.memory = memory;
        }
    }

    public static final class ProvisioningResource implements APIResource {

        private String memoryGB;

        @DataBoundConstructor
        public ProvisioningResource(String memoryGB) {
            this.memoryGB = memoryGB;
        }

        @Override
        public APIExportable<?> toModel() {
            return new Provisioning(memoryGB);
        }

        public String getMemoryGB() {
            return memoryGB;
        }

        public void setMemoryGB(String memoryGB) {
            this.memoryGB = memoryGB;
        }
    }
}
