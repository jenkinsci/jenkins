package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import hudson.ExtensionPoint;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class ExtensionTypeHierarchyTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    public interface Animal extends ExtensionPoint {}

    public interface White extends ExtensionPoint {}

    @TestExtension
    public static class Swan implements Animal, White {}

    @TestExtension
    public static class Crow implements Animal {}

    /**
     * Swan is both white and animal, so a single swan instance gets listed to both.
     */
    @Test
    void sameExtensionCanImplementMultipleExtensionPoints() {
        Animal[] animals = sort(j.jenkins.getExtensionList(Animal.class).toArray(new Animal[2]));
        assertThat(animals[0], instanceOf(Crow.class));
        assertThat(animals[1], instanceOf(Swan.class));
        assertEquals(2, animals.length);

        White[] whites = sort(j.jenkins.getExtensionList(White.class).toArray(new White[1]));
        assertThat(whites[0], instanceOf(Swan.class));
        assertEquals(1, whites.length);

        assertSame(animals[1], whites[0]);
    }

    /**
     * Sort by class name
     */
    private <T> T[] sort(T[] a) {
        Arrays.sort(a, Comparator.comparing(o -> o.getClass().getName()));
        return a;
    }
}
