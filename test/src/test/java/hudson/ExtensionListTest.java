package hudson;

import static org.junit.Assert.*;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.DescriptorList;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ExtensionListTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    // Interface for non-Descriptor extension point
    public interface Animal extends ExtensionPoint {}

    @Extension
    public static class Dog implements Animal {}

    @Extension
    public static class Cat implements Animal {}

    @Test
    public void autoDiscovery() {
        ExtensionList<Animal> list = ExtensionList.lookup(Animal.class);
        assertEquals(2, list.size());
        assertNotNull(list.get(Dog.class));
        assertNotNull(list.get(Cat.class));
    }

    @Test
    @WithoutJenkins
    public void nullJenkinsInstance() {
        ExtensionList<Animal> list = ExtensionList.lookup(Animal.class);
        assertTrue(list.isEmpty());
    }

    @Test
    public void extensionListView() {
        List<Animal> LIST = ExtensionListView.createList(Animal.class);

        assertEquals(2, LIST.size());
        assertTrue(LIST.stream().anyMatch(d -> d.getClass() == Dog.class));
        assertTrue(LIST.stream().anyMatch(d -> d.getClass() == Cat.class));

        Animal lion = new Animal() {};
        LIST.add(lion);
        assertEquals(3, LIST.size());
        assertTrue(LIST.contains(lion));
    }

    public abstract static class FishDescriptor extends Descriptor<Fish> {}

    public abstract static class Fish implements Describable<Fish> {
        @Override
        public Descriptor<Fish> getDescriptor() {
            return Jenkins.get().getDescriptor(getClass());
        }
    }

    public static class Tai extends Fish {
        @Extension public static final class DescriptorImpl extends FishDescriptor {}
    }

    public static class Saba extends Fish {
        @Extension public static final class DescriptorImpl extends FishDescriptor {}
    }

    public static class Sishamo extends Fish {
        public static final class DescriptorImpl extends FishDescriptor {}
    }

    @Test
    public void descriptorLookup() {
        Descriptor<Fish> d = new Sishamo().getDescriptor();
        DescriptorExtensionList<Fish, Descriptor<Fish>> list = j.jenkins.getDescriptorList(Fish.class);
        assertSame(d, list.get(Sishamo.DescriptorImpl.class));
        assertSame(d, j.jenkins.getDescriptor(Sishamo.class));
    }

    @Test
    public void fishDiscovery() {
        DescriptorList<Fish> LIST = new DescriptorList<>(Fish.class);
        DescriptorExtensionList<Fish, Descriptor<Fish>> list = j.jenkins.getDescriptorList(Fish.class);

        assertEquals(2, list.size());
        LIST.add(new Sishamo.DescriptorImpl());
        assertEquals(3, list.size());

        assertTrue(LIST.stream().allMatch(d -> list.contains(d)));
    }

    @Test
    public void legacyDescriptorList() {
        DescriptorList<Fish> LIST = new DescriptorList<>();
        assertTrue(LIST.isEmpty());

        LIST.add(new Sishamo.DescriptorImpl());
        assertEquals(1, LIST.size());

        LIST = new DescriptorList<>();
        assertTrue(LIST.isEmpty());
    }

    public static class Car implements ExtensionPoint {
        final String name;
        public Car(String name) { this.name = name; }
    }

    @Extension(ordinal = 1)
    public static class Toyota extends Car { public Toyota() { super("toyota"); }}

    @Extension(ordinal = 3)
    public static Car honda() { return new Car("honda"); }

    @Extension(ordinal = 2)
    public static final Car mazda = new Car("mazda");

    @Test
    public void ordinals() {
        ExtensionList<Car> list = j.jenkins.getExtensionList(Car.class);
        assertEquals("honda", list.get(0).name);
        assertEquals("mazda", list.get(1).name);
        assertEquals("toyota", list.get(2).name);
    }

    @Issue("JENKINS-39520")
    @Test
    public void removeAll() {
        ExtensionList<Animal> list = ExtensionList.lookup(Animal.class);
        assertTrue(list.removeAll(new ArrayList<>(list)));
        assertTrue(list.isEmpty());
    }

    @Issue("JENKINS-62056")
    @Test
    public void checkSort() {
        ExtensionList.lookup(Object.class).get(0); // Test sorting without exceptions
    }
}
