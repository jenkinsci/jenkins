package hudson;

import org.jvnet.hudson.test.HudsonTestCase;
import hudson.model.Descriptor;
import hudson.model.Describable;
import hudson.model.Hudson;
import hudson.util.DescriptorList;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;

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

    public void testExtensionListView() throws Exception {
        // this is how legacy list like UserNameResolver.LIST gets created.
        List<Animal> LIST = ExtensionListView.createList(Animal.class);

        // we should see auto-registered instances here
        assertEquals(2,LIST.size());
        assertTrue(hasInstanceOf(LIST,Dog.class));
        assertTrue(hasInstanceOf(LIST,Cat.class));

        Animal lion = new Animal() {};
        LIST.add(lion);
        assertEquals(3,LIST.size());
        assertTrue(LIST.contains(lion));
    }

    private boolean hasInstanceOf(Collection c, Class type) {
        for (Object o : c)
            if(o.getClass()==type)
                return true;
        return false;
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

    /**
     * Verifies that the automated {@link Descriptor} lookup works.
     */
    public void testDescriptorLookup() throws Exception {
        Descriptor<Fish> d = new Sishamo().getDescriptor();

        DescriptorExtensionList<Fish,Descriptor<Fish>> list = hudson.getDescriptorList(Fish.class);
        assertSame(d,list.get(Sishamo.DescriptorImpl.class));

        assertSame(d,hudson.getDescriptor(Sishamo.class));
    }

    public void testFishDiscovery() throws Exception {
        // imagine that this is a static instance, like it is in many LIST static field in Hudson.
        DescriptorList<Fish> LIST = new DescriptorList<Fish>(Fish.class);

        DescriptorExtensionList<Fish,Descriptor<Fish>> list = hudson.getDescriptorList(Fish.class);
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

        // DescriptorList can be gone and new one created but it should still have the same list
        LIST = new DescriptorList<Fish>(Fish.class);
        assertEquals(3,LIST.size());
        assertNotNull(LIST.findByName(Tai.class.getName()));
        assertNotNull(LIST.findByName(Sishamo.class.getName()));
        assertNotNull(LIST.findByName(Saba.class.getName()));
    }

    public void testLegacyDescriptorList() throws Exception {
        // created in a legacy fashion without any tie to ExtensionList
        DescriptorList<Fish> LIST = new DescriptorList<Fish>();

        // we won't auto-discover anything
        assertEquals(0,LIST.size());

        // registration can happen later, and it should be still visible
        LIST.add(new Sishamo.DescriptorImpl());
        assertEquals(1,LIST.size());
        assertNotNull(LIST.findByName(Sishamo.class.getName()));

        // create a new list and it forgets everything.
        LIST = new DescriptorList<Fish>();
        assertEquals(0,LIST.size());
    }
}
