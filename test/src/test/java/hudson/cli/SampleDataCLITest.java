package hudson.cli;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.data.CustomDataModel;
import jenkins.data.DataContext;
import jenkins.data.DataModel;
import jenkins.data.JsonSerializer;
import jenkins.data.Serializer;
import jenkins.data.VersionedEnvelope;
import jenkins.data.exportable.APIExportable;
import jenkins.data.exportable.APIResource;
import jenkins.data.tree.Mapping;
import jenkins.data.tree.Scalar;
import jenkins.data.tree.TreeNode;
import jenkins.model.Jenkins;
import org.apache.tools.ant.filters.StringInputStream;
import org.jenkinsci.Symbol;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;

public class SampleDataCLITest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void sampleDataTest() {
        CLICommandInvoker invoker = new CLICommandInvoker(jenkins, new SampleDataCLI());
        CLICommandInvoker.Result result = invoker.withStdin(new StringInputStream("{\"version\": 1,\"data\": [{\"memoryGB\": 2}]}")).invoke();
        System.out.println(result.stdout());
    }

    @Test
    public void sampleDataFruitTest() {
        CLICommandInvoker invoker = new CLICommandInvoker(jenkins, new SampleFruitDataCLI());
        CLICommandInvoker.Result result = invoker.withStdin(new StringInputStream(
                "{\"version\": 1,\"data\": [{\"type\": \"banana\", \"yellow\": true}, {\"type\": \"apple\", \"seeds\": 22}]}")).invoke();
        System.out.println(result.stdout());
    }


    @Extension
    public static class SampleDataCLI extends CLICommand {

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

        // Antonio: Ideally this class should not require to know about the existence of its APIResource
        // ProvisioningResource should be able to register itself as the APIResource for this
        public static final class Provisioning implements APIExportable<SampleDataCLI.ProvisioningResource> {

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

    // Fruit shop

    @Extension
    public static class SampleFruitDataCLI extends CLICommand {

        private boolean read = true;

        @Override
        public String getShortDescription() {
            return "Fruits";
        }

        @Override
        protected int run() throws Exception {
            if (read) {
                VersionedEnvelope<Fruit> envelope = new JsonSerializer().read(Fruit.class, stdin);
                for (Fruit res : envelope.getData()) {
                    System.out.println(res);
                }
                return 0;
            } else {
                VersionedEnvelope<Fruit> data = new VersionedEnvelope<>(1, Arrays.asList(
                        new Apple(2),
                        new Banana(true)
                ));
                new JsonSerializer().write(data, stdout);
                return 0;
            }
        }
    }

    public static abstract class Fruit implements ExtensionPoint, Describable<Fruit> {
        protected String name;
        protected Fruit(String name) { this.name = name; }

        public Descriptor<Fruit> getDescriptor() {
            return Jenkins.getInstance().getDescriptor(getClass());
        }
    }

    public static class FruitDescriptor extends Descriptor<Fruit> {}

    /**
     * Implicit inline model where in-memory format and the data format is identical.
     */
    public static class Apple extends Fruit {
        private int seeds;
        @DataBoundConstructor
        public Apple(int seeds) {
            super("Apple");
            this.seeds = seeds;
        }
        @Extension @Symbol("apple")
        public static final class DescriptorImpl extends FruitDescriptor {}
    }

    /**
     * Custom binder falling back to the default reflection-based reader
     */
    public static class Banana extends Fruit {
        private boolean yellow;
        @DataBoundConstructor
        public Banana(boolean yellow) {
            super("Banana");
            this.yellow = yellow;
        }

        public boolean isYellow() {
            return yellow;
        }

        @Extension @Symbol("banana")
        public static final class DescriptorImpl extends FruitDescriptor {}
    }

    @Extension
    public static class BananaModel extends CustomDataModel<Banana> {
        public BananaModel() {
            super(Banana.class,
                    parameter("ripe",boolean.class));
        }

        @Override
        public TreeNode write(Banana object, DataContext context) {
            Mapping m = new Mapping();
            m.put("ripe",object.yellow);
            return m;
        }

        @Override
        public Banana read(TreeNode input, DataContext context) throws IOException {
            Mapping m = input.asMapping();
            m.put("yellow",m.get("ripe"));
            DataModel<Banana> std = DataModel.byReflection(Banana.class);
            return std.read(input, context);
        }
    }

    /**
     * Custom serializer from scratch, no delegation to default.
     */
    public static class Cherry extends Fruit {
        private String color;

        public Cherry(String c) {
            super("Cherry");
            this.color = c;
        }

        public String color() { // don't need to be following convention
            return color;
        }

        @Extension @Symbol("cherry")
        public static final class DescriptorImpl extends FruitDescriptor {}
    }

    @Extension
    public static class CherryModel extends CustomDataModel<Cherry> {
        public CherryModel() {
            // TODO: in this example, cherry binds to a scalar, so how do you go about parameters?
            super(Cherry.class);
        }

        @Override
        public TreeNode write(Cherry object, DataContext context) {
            return new Scalar(object.color());
        }

        @Override
        public Cherry read(TreeNode input, DataContext context) throws IOException {
            return new Cherry(input.asScalar().getValue());
        }
    }

    /**
     * Example where 'contract' is defined elsewhere explicitly as a separate resource class
     */
    public static class Durian extends Fruit implements APIExportable {
        private float age;

        // some other gnary fields that you don't want to participate in the format

        public Durian(float age) {
            super("Durian");
            this.age = age;
        }

        // lots of gnary behaviours

        public DurianResource toResource() {
            return new DurianResource(age>30.0f);
        }

        @Extension @Symbol("durian")
        public static final class DescriptorImpl extends FruitDescriptor {}
    }

    /**
     * Model object that's defined as contract. This is the class that gets data-bound.
     */
    public static class DurianResource implements APIResource {
        private boolean smelly;

        @DataBoundConstructor
        public DurianResource(boolean smelly) {
            this.smelly = smelly;
        }

        public boolean isSmelly() {
            return smelly;
        }

        public Durian toModel() {
            return new Durian(smelly?45.0f:15.0f);
        }

        // no behavior
    }

    // Jesse sees this more as a convenience sugar, not a part of the foundation,
    // in which case helper method like this is preferrable over interfaces that 'invade' model objects
    //
    // Kohsuke notes that, channeling Antonio & James N & co, the goal is to make the kata more explicit,
    // so this would go against that.
    //
    // either way, we'd like to establish that these can be implemented as sugar
    @Extension(optional=true)
    public static DataModel<Durian> durianBinder() {
        return DataModel.byTranslation(DurianResource.class,
                dr -> new Durian(dr.smelly ? 45 : 15),
                d -> new DurianResource(d.age > 30));
    }


    /**
     * Variant of a Durian example in the form closer to Antonio's original proposal.
     *
     * This has the effect of making the idiom more explicit.
     */
    public static class Eggfruit extends Fruit implements APIExportable<EggfruitResource> {
        private float age;

        // some other gnary fields that you don't want to participate in the format

        public Eggfruit(float age) {
            super("Eggfruit");
            this.age = age;
        }

        // lots of gnary behaviours

        public EggfruitResource toResource() {
            return new EggfruitResource(age>30.0f);
        }

        @Extension @Symbol("eggfruit")
        public static final class DescriptorImpl extends FruitDescriptor {}
    }

    /**
     * Model object that's defined as contract. This is the class that gets data-bound.
     */
    public static class EggfruitResource implements APIResource {
        private boolean smelly;

        @DataBoundConstructor
        public EggfruitResource(boolean smelly) {
            this.smelly = smelly;
        }

        public boolean isSmelly() {
            return smelly;
        }

        public Eggfruit toModel() {
            return new Eggfruit(smelly?45.0f:15.0f);
        }

        // no behavior
    }

}
