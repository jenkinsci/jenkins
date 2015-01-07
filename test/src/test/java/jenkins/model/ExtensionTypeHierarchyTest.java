package jenkins.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import hudson.ExtensionPoint;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.util.Arrays;
import java.util.Comparator;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExtensionTypeHierarchyTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    public static interface Animal extends ExtensionPoint {}
    public static interface White extends ExtensionPoint {}

    @TestExtension
    public static class Swan implements Animal, White {}
    @TestExtension
    public static class Crow implements Animal {}

    /**
     * Swan is both white and animal, so a single swan instance gets listed to both.
     */
    @Test
    public void sameExtensionCanImplementMultipleExtensionPoints() {
        Animal[] animals = sort(j.jenkins.getExtensionList(Animal.class).toArray(new Animal[2]));
        assertTrue(animals[0] instanceof Crow);
        assertTrue(animals[1] instanceof Swan);
        assertEquals(2, animals.length);

        White[] whites = sort(j.jenkins.getExtensionList(White.class).toArray(new White[1]));
        assertTrue(whites[0] instanceof Swan);
        assertEquals(1, whites.length);

        assertSame(animals[1], whites[0]);
    }

    /**
     * Sort by class name
     */
    private <T> T[] sort(T[] a) {
        Arrays.sort(a,new Comparator<T>() {
            public int compare(T o1, T o2) {
                return o1.getClass().getName().compareTo(o2.getClass().getName());
            }
        });
        return a;
    }
}
