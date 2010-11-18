package hudson.plugins.ui_samples;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.util.XStream2;
import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * @author Alan.Harder@oracle.com
 */
@Extension
public class DropdownList extends UISample {

    @Override
    public String getDescription() {
        return "Show different form elements based on choice in a &lt;select> control";
    }

    public Fruit getFruit() {
        // Could return currently configured/saved item here to initialized form with this data
        return null;
    }

    public DescriptorExtensionList<Fruit,Descriptor<Fruit>> getFruitDescriptors() {
        return Hudson.getInstance().<Fruit,Descriptor<Fruit>>getDescriptorList(Fruit.class);
    }

    // Process form data and show it as serialized XML
    public void doSubmit(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        // stapler-class in form data tells Stapler which Fruit subclass to use
        Fruit fruit = req.bindJSON(Fruit.class, req.getSubmittedForm().getJSONObject("fruit"));
        rsp.setContentType("text/plain");
        new XStream2().toXML(fruit, rsp.getWriter());
    }

    @Override
    public List<SourceFile> getSourceFiles() {
        List<SourceFile> list = new java.util.ArrayList<SourceFile>(super.getSourceFiles());
        list.add(new SourceFile("Apple/config.jelly"));
        list.add(new SourceFile("Banana/config.jelly"));
        return list;
    }

    @Extension
    public static final class DescriptorImpl extends UISampleDescriptor {
    }

    public static class Fruit implements ExtensionPoint, Describable<Fruit> {
        protected String name;
        private Fruit(String name) { this.name = name; }

        public Descriptor<Fruit> getDescriptor() {
            return Hudson.getInstance().getDescriptor(getClass());
        }
    }

    public static class FruitDescriptor extends Descriptor<Fruit> {
        public FruitDescriptor(Class<? extends Fruit> clazz) {
            super(clazz);
        }
        public String getDisplayName() {
            return clazz.getSimpleName();
        }
    }

    public static class Apple extends Fruit {
        private int seeds;
        @DataBoundConstructor public Apple(int seeds) {
            super("Apple");
            this.seeds = seeds;
        }
        @Extension public static final FruitDescriptor D = new FruitDescriptor(Apple.class);
    }

    public static class Banana extends Fruit {
        private boolean yellow;
        @DataBoundConstructor public Banana(boolean yellow) {
            super("Banana");
            this.yellow = yellow;
        }
        @Extension public static final FruitDescriptor D = new FruitDescriptor(Banana.class);
    }
}
