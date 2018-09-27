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
     * Custom marshaller falling back to the default reflection-based reader
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

    // exporter (not necessarily in core, could be a plugin):

    @Binds(Banana.class)
    public class BananaBinder implements ModelBinder<Banana> {
        @Override
        public CNode write(Banana object, DataContext context) {
            Mapping m = new Mapping();
            m.put("ripe",object.yellow);
            return m;
        }

        @Override
        public Banana read(CNode input, DataContext context) {
            Mapping m = input.asMapping();
            m.put("yellow",m.get("ripe"));
            ModelBinder<Banana> std = ModelBinder.byReflection(Banana.class);
            return std.read(input, context);

//            return new DefaultModelBinder(Banana.class).read(m,context);

//            return context.readDefault(Banana.class,m);
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
        public CNode write(Cherry object, DataContext context) {
            return new Scalar(object.color());
        }

        @Override
        public Cherry read(CNode input, DataContext context) {
            return new Cherry(input.asScalar().getValue());
        }
    }



    void fruitSample() {
        // API usage:

        FreeStyleProject p = ...
        APIResource resource = ModelExporter.getExporterFor(p.getClass()).fromModel(p);

        APIResource r = deserializeInput(inputString);
        FreeStyleProject p = ModelExporter.getExporterFor(FreeStyleProject.class).toModel(r);
    }

}
