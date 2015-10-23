package hudson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import jenkins.model.Jenkins;
import hudson.model.Descriptor;
import hudson.model.Describable;
import hudson.util.DescriptorList;

import java.util.List;
import java.util.Collection;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExtensionListTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

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


    @Test
    public void autoDiscovery() throws Exception {
        ExtensionList<Animal> list = ExtensionList.lookup(Animal.class);
        assertEquals(2,list.size());
        assertNotNull(list.get(Dog.class));
        assertNotNull(list.get(Cat.class));
    }

    @Test
    @WithoutJenkins
    public void nullJenkinsInstance() throws Exception {
        ExtensionList<Animal> list = ExtensionList.lookup(Animal.class);
        assertEquals(0, list.size());
        assertFalse(list.iterator().hasNext());
    }

    @Test
    public void extensionListView() throws Exception {
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

    public static abstract class FishDescriptor extends Descriptor<Fish> {}

    public static abstract class Fish implements Describable<Fish> {
        public Descriptor<Fish> getDescriptor() {
            return Jenkins.getInstance().getDescriptor(getClass());
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
    @Test
    public void descriptorLookup() throws Exception {
        Descriptor<Fish> d = new Sishamo().getDescriptor();

        DescriptorExtensionList<Fish,Descriptor<Fish>> list = j.jenkins.<Fish,Descriptor<Fish>>getDescriptorList(Fish.class);
        assertSame(d,list.get(Sishamo.DescriptorImpl.class));

        assertSame(d, j.jenkins.getDescriptor(Sishamo.class));
    }

    @Test
    public void fishDiscovery() throws Exception {
        // imagine that this is a static instance, like it is in many LIST static field in Hudson.
        DescriptorList<Fish> LIST = new DescriptorList<Fish>(Fish.class);

        DescriptorExtensionList<Fish,Descriptor<Fish>> list = j.jenkins.<Fish,Descriptor<Fish>>getDescriptorList(Fish.class);
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

    @Test
    public void legacyDescriptorList() throws Exception {
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

    public static class Car implements ExtensionPoint {
        final String name;

        public Car(String name) {
            this.name = name;
        }
    }

    @Extension(ordinal=1)
    public static class Toyota extends Car {
        public Toyota() {
            super("toyota");
        }
    }

    @Extension(ordinal=3)
    public static Car honda() { return new Car("honda"); }


    @Extension(ordinal=2)
    public static final Car mazda = new Car("mazda");

    /**
     * Makes sure sorting of the components work as expected.
     */
    @Test
    public void ordinals() {
        ExtensionList<Car> list = j.jenkins.getExtensionList(Car.class);
        assertEquals("honda",list.get(0).name);
        assertEquals("mazda",list.get(1).name);
        assertEquals("toyota",list.get(2).name);
    }
}
