package hudson.model;

import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class DescriptorTest {

    private static class DescribableImpl implements Describable<DescribableImpl> {

        private Descriptor<DescribableImpl> descriptor;

        @Override
        public Descriptor<DescribableImpl> getDescriptor() {
            return descriptor;
        }

        private static class DummyDescriptor extends Descriptor<DescriptorTest.DescribableImpl> {
        }
    }

    @Issue("JENKINS-45977")
    @Test
    public void testToMap() throws Exception {
        DescribableImpl describable1 = new DescribableImpl();
        describable1.descriptor = new DescribableImpl.DummyDescriptor();
        DescribableImpl describable2 = new DescribableImpl();
        describable2.descriptor = new DescribableImpl.DummyDescriptor();
        Iterable<DescribableImpl> describables = Arrays.asList(describable1, describable2);
        Map<Descriptor<DescribableImpl>, DescribableImpl> map1 = Descriptor.toMap(describables);
        Map<Descriptor<DescribableImpl>, DescribableImpl> expected = new HashMap<>();
        expected.put(describable1.descriptor, describable1);
        expected.put(describable2.descriptor, describable2);
        assertMapEquals(expected, map1);

        // Descriptor might be null
        expected.remove(describable1.descriptor);
        describable1.descriptor = null;
        Map<Descriptor<DescribableImpl>, DescribableImpl> map2 = Descriptor.toMap(describables);
        assertMapEquals(expected, map2);
    }

    private void assertMapEquals(Map map, Map other) {
        assertNotNull(map);
        assertNotNull(other);
        assertEquals(map.size(), other.size());
        for (Object key : map.keySet()) {
            assertEquals(map.get(key), other.get(key));
        }
    }
}
