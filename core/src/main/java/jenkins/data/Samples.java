package jenkins.data;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import jenkins.data.model.CNode;
import jenkins.data.model.Mapping;
import jenkins.data.model.Scalar;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Here is how the Data API gets consumed by plugin devs.
 *
 * <ul>
 *     <li>How to write a custom serializer
 *
 * @author Kohsuke Kawaguchi
 */
public class Samples {
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
        @DataBoundConstructor public Apple(int seeds) {
            super("Apple");
            this.seeds = seeds;
        }
        @Extension
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

        @Extension
        public static final class DescriptorImpl extends FruitDescriptor {}
    }

    @Binds(Banana.class)
    public class BananaBinder implements ModelBinder<Banana> {
        @Override
        public CNode write(Banana object, WriteDataContext context) {
            Mapping m = new Mapping();
            m.put("ripe",object.yellow);
            return m;
        }

        @Override
        public Banana read(CNode input, ReadDataContext context) {
            Mapping m = input.asMapping();
            m.put("yellow",m.get("ripe"));
            ModelBinder<Banana> std = ModelBinder.byReflection(Banana.class);
            return std.read(input, context);
        }

        /**
         * When hand-writing a binder, one needs to also hand-code schema
         */
        @Override
        public Collection<DescribableParameter> getParameters() {
            return Arrays.asList(
                new DescribableParameter(this,Boolean.class,"ripe",null)
            );
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

        @Extension
        public static final class DescriptorImpl extends FruitDescriptor {}
    }

    @Binds(Cherry.class)
    public class CherryBinder implements ModelBinder<Cherry> {
        @Override
        public CNode write(Cherry object, WriteDataContext context) {
            return new Scalar(object.color());
        }

        @Override
        public Cherry read(CNode input, ReadDataContext context) {
            return new Cherry(input.asScalar().getValue());
        }

        /**
         * When hand-writing a binder, one needs to also hand-code schema
         */
        @Override
        public Collection<DescribableParameter> getParameters() {
            // TODO: in this example, cherry binds to a scalar, so how do you go about that?
            throw new UnsupportedOperationException();
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

        @Extension
        public static final class DescriptorImpl extends FruitDescriptor {}
    }

    /**
     * Model object that's defined as contract. This is the class that gets data-bound.
     */
    public static class DurianResource implements APIResource {
        private boolean smelly;

        @DataBoundConstructor
        DurianResource(boolean smelly) {
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
    @Binds(Durian.class)
    public static ModelBinder<Durian> durianBinder() {
        return ModelBinder.byTranslation(DurianResource.class,
                dr -> new Durian(dr.smelly ? 45 : 15),
                d -> new DurianResource(d.age > 30));
    }

    /**
     * This would be a part of the system, not a part of the user-written code.
     */
    @Binds(APIExportable.class)
    public class APIExportableBinder implements ModelBinder<APIExportable> {
        @Override
        public CNode write(APIExportable object, WriteDataContext context) {
            APIResource r = object.toResource();
            ModelBinder std = context.getReflectionBinder(r.getClass());
            return std.write(r, context);
        }

        @Override
        public APIExportable read(CNode input, ReadDataContext context) {
            ModelBinder<APIResource> std = ModelBinder.byReflection(context.expectedType());
            return std.read(input, context).toModel();
        }
    }



//    public static class GitSCM extends Fruit {
//        private List<UserRemoteConfig> userRemoteConfigs;
//        private transient List<RemoteConfig> remoteRepositories;
//    }
//
//    public class UserRemoteConfig extends AbstractDescribableImpl<UserRemoteConfig> {
//        private String name;
//        private String refspec;
//        private String url;
//        private String credentialsId;
//    }



    /**
     * Read from stdin and construct model object
     */
    public void cliReaderSample() {
            VersionedResource<Fruit> envelope = new JsonSerializer().read(Fruit.class, System.in);
            for (Fruit res : envelope.getData()) {
                System.out.println(res);
            }

//            VersionedResource input = JSONCLICommandHelper.readInput(stdin);
//            for (Object res : (Collection) input.getData()) {
//                DomainCredentials domainCredentials = (DomainCredentials) ((APIResource) res).toModel();
//                Domain domain = domainCredentials.getDomain();
//                if (domainCredentials.getDomain().getName() == null) {
//                    domain = Domain.global();
//                }
//                for (Credentials creds : domainCredentials.getCredentials()) {
//                    store.addCredentials(domain, creds);
//                }
//            }
    }

    public void cliWriterExample() {
        VersionedResource<Fruit> d = new VersionedResource<>(1, Arrays.asList(
                new Apple(3),
                new Banana(true),
                new Cherry("orange"),
                new Durian(35.0f)
        ));

        new JsonSerializer().write(d, System.out);
    }

    // TODO: does this replace structs for Pipeline?
    // TODO: does this replace CasC?


}
