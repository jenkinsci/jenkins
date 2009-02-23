package hudson;

import org.jvnet.hudson.test.HudsonTestCase;
import hudson.model.Descriptor;
import hudson.model.Describable;
import hudson.model.Hudson;
import hudson.util.DescriptorList;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExtensionListTest extends HudsonTestCase {
//
//
// non-Descriptor extension point
//
//

    public interface Animal extends ExtensionPoint {
    }

    @Extension
    public static class Dog implements Animal {
    }

    @Extension
    public static class Cat implements Animal {
    }


    public void testAutoDiscovery() throws Exception {
        ExtensionList<Animal> list = hudson.getExtensionList(Animal.class);
        assertEquals(2,list.size());
        assertNotNull(list.get(Dog.class));
        assertNotNull(list.get(Cat.class));
    }

//
//
// Descriptor extension point
//
//

    public static abstract class FishDescriptor extends Descriptor<Fish> {
        public String getDisplayName() {
            return clazz.getName();
        }
    }

    public static abstract class Fish implements Describable<Fish> {
        public Descriptor<Fish> getDescriptor() {
            return Hudson.getInstance().getDescriptor(getClass());
        }
    }

    public static class Tai extends Fish {
        @Extension
        public static final class DescriptorImpl extends FishDescriptor {}
    }

    public static class Saba extends Fish {
        @Extension
        public static final class DescriptorImpl extends FishDescriptor {}
    }

    public static class Sishamo extends Fish {
        public static final class DescriptorImpl extends FishDescriptor {}
    }

    public void testFishDiscovery() throws Exception {
        // imagine that this is a static instance, like it is in many LIST static field in Hudson.
        DescriptorList<Fish> LIST = new DescriptorList<Fish>(Fish.class);

        DescriptorExtensionList<Fish> list = hudson.getDescriptorList(Fish.class);
        assertEquals(2,list.size());
        assertNotNull(list.get(Tai.DescriptorImpl.class));
        assertNotNull(list.get(Saba.DescriptorImpl.class));

        // registration can happen later, and it should be still visible
        LIST.add(new Sishamo.DescriptorImpl());
        assertEquals(3,list.size());
        assertNotNull(list.get(Sishamo.DescriptorImpl.class));

        // all 3 should be visisble from LIST, too
        assertEquals(3,LIST.size());
        assertNotNull(LIST.findByName(Tai.class.getName()));
        assertNotNull(LIST.findByName(Sishamo.class.getName()));
        assertNotNull(LIST.findByName(Saba.class.getName()));
    }
}
