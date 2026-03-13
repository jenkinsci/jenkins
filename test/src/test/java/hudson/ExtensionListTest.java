package hudson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.DescriptorList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
public class ExtensionListTest {

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
    void autoDiscovery(JenkinsRule j) {
        ExtensionList<Animal> list = ExtensionList.lookup(Animal.class);
        assertEquals(2, list.size());
        assertNotNull(list.get(Dog.class));
        assertNotNull(list.get(Cat.class));
    }

    @Test
    @WithoutJenkins
    void nullJenkinsInstance() {
        ExtensionList<Animal> list = ExtensionList.lookup(Animal.class);
        assertEquals(0, list.size());
        assertFalse(list.iterator().hasNext());
    }

    @Test
    void extensionListView(JenkinsRule j) {
        // this is how legacy list like UserNameResolver.LIST gets created.
        List<Animal> LIST = ExtensionListView.createList(Animal.class);

        // we should see auto-registered instances here
        assertEquals(2, LIST.size());
        assertTrue(hasInstanceOf(LIST, Dog.class));
        assertTrue(hasInstanceOf(LIST, Cat.class));

        Animal lion = new Animal() {};
        LIST.add(lion);
        assertEquals(3, LIST.size());
        assertTrue(LIST.contains(lion));
    }

    private boolean hasInstanceOf(Collection c, Class type) {
        for (Object o : c)
            if (o.getClass() == type)
                return true;
        return false;
    }

//
//
// Descriptor extension point
//
//

    public abstract static class FishDescriptor extends Descriptor<Fish> {}

    public abstract static class Fish implements Describable<Fish> {
        @Override
        public Descriptor<Fish> getDescriptor() {
            return Jenkins.get().getDescriptor(getClass());
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
    void descriptorLookup(JenkinsRule j) {
        Descriptor<Fish> d = new Sishamo().getDescriptor();

        DescriptorExtensionList<Fish, Descriptor<Fish>> list = j.jenkins.getDescriptorList(Fish.class);
        assertSame(d, list.get(Sishamo.DescriptorImpl.class));

        assertSame(d, j.jenkins.getDescriptor(Sishamo.class));
    }

    @Test
    void fishDiscovery(JenkinsRule j) {
        // imagine that this is a static instance, like it is in many LIST static field in Hudson.
        DescriptorList<Fish> LIST = new DescriptorList<>(Fish.class);

        DescriptorExtensionList<Fish, Descriptor<Fish>> list = j.jenkins.getDescriptorList(Fish.class);
        assertEquals(2, list.size());
        assertNotNull(list.get(Tai.DescriptorImpl.class));
        assertNotNull(list.get(Saba.DescriptorImpl.class));

        // registration can happen later, and it should be still visible
        LIST.add(new Sishamo.DescriptorImpl());
        assertEquals(3, list.size());
        assertNotNull(list.get(Sishamo.DescriptorImpl.class));

        // all 3 should be visible from LIST, too
        assertEquals(3, LIST.size());
        assertNotNull(LIST.findByName(Tai.class.getName()));
        assertNotNull(LIST.findByName(Sishamo.class.getName()));
        assertNotNull(LIST.findByName(Saba.class.getName()));

        // DescriptorList can be gone and new one created but it should still have the same list
        LIST = new DescriptorList<>(Fish.class);
        assertEquals(3, LIST.size());
        assertNotNull(LIST.findByName(Tai.class.getName()));
        assertNotNull(LIST.findByName(Sishamo.class.getName()));
        assertNotNull(LIST.findByName(Saba.class.getName()));
    }

    @Test
    void legacyDescriptorList(JenkinsRule j) {
        // created in a legacy fashion without any tie to ExtensionList
        DescriptorList<Fish> LIST = new DescriptorList<>();

        // we won't auto-discover anything
        assertEquals(0, LIST.size());

        // registration can happen later, and it should be still visible
        LIST.add(new Sishamo.DescriptorImpl());
        assertEquals(1, LIST.size());
        assertNotNull(LIST.findByName(Sishamo.class.getName()));

        // create a new list and it forgets everything.
        LIST = new DescriptorList<>();
        assertEquals(0, LIST.size());
    }

    public static class Car implements ExtensionPoint {
        final String name;

        public Car(String name) {
            this.name = name;
        }
    }

    @Extension(ordinal = 1)
    public static class Toyota extends Car {
        public Toyota() {
            super("toyota");
        }
    }

    @Extension(ordinal = 3)
    public static Car honda() { return new Car("honda"); }


    @Extension(ordinal = 2)
    public static final Car mazda = new Car("mazda");

    /**
     * Makes sure sorting of the components work as expected.
     */
    @Test
    void ordinals(JenkinsRule j) {
        ExtensionList<Car> list = j.jenkins.getExtensionList(Car.class);
        assertEquals("honda", list.get(0).name);
        assertEquals("mazda", list.get(1).name);
        assertEquals("toyota", list.get(2).name);
    }

    @Issue("JENKINS-39520")
    @Test
    void removeAll(JenkinsRule j) {
        ExtensionList<Animal> list = ExtensionList.lookup(Animal.class);
        assertTrue(list.removeAll(new ArrayList<>(list)));
        assertEquals(0, list.size());
        assertFalse(list.removeAll(new ArrayList<>(list)));
        assertEquals(0, list.size());
    }

    @Issue("JENKINS-62056")
    @Test
    void checkSort(JenkinsRule j) {
        ExtensionList.lookup(Object.class).getFirst(); // exceptions are a problem
    }
}
