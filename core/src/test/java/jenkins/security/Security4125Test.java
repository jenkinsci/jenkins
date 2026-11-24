package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thoughtworks.xstream.security.ForbiddenClassException;
import hudson.remoting.ClassFilter;
import hudson.util.XStream2;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import jenkins.util.xstream.CriticalXStreamException;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

public class Security4125Test {

    @Test
    @Issue("SECURITY-4125")
    void resolvesToBlacklistedTypeIsRejected() {
        ClassFilter.setDefault(new ClassFilter() {
            @Override
            public boolean isBlacklisted(Class<?> c) {
                return "com.google.common.collect.ImmutableSortedMultiset".equals(c.getName());
            }

            @Override
            public boolean isBlacklisted(String name) {
                return "com.google.common.collect.ImmutableSortedMultiset".equals(name);
            }
        });
        try {
            XStream2 xs = new XStream2();
            xs.alias("hold", HoldRT.class);
            CriticalXStreamException ex = assertThrows(CriticalXStreamException.class, () ->
                    xs.fromXML("<hold><nested resolves-to=\"com.google.common.collect.ImmutableSortedMultiset\"/></hold>"));
            assertThat(ex.getMessage(), containsString("cause-exception     : " + ForbiddenClassException.class.getName()));
        } finally {
            ClassFilter.setDefault(ClassFilter.STANDARD);
        }
    }

    @Test
    @Issue("SECURITY-4125")
    void resolvesToAllowedTypeIsAccepted() {
        ClassFilter.setDefault(ClassFilter.NONE);
        try {
            XStream2 xs = new XStream2();
            xs.alias("hold", HoldRT.class);
            HoldRT hold = (HoldRT) xs.fromXML(
                    "<hold><nested resolves-to=\"" + RTProxy.class.getName() + "\"/></hold>");
            assertThat(hold.nested, instanceOf(HoldRT.class));
        } finally {
            ClassFilter.setDefault(ClassFilter.STANDARD);
        }
    }

    @Test
    @Issue("SECURITY-4125")
    void resolvesToBlacklistedTypeOnRootIsRejected() {
        ClassFilter.setDefault(new ClassFilter() {
            @Override
            public boolean isBlacklisted(Class<?> c) {
                return "com.google.common.collect.ImmutableSortedMultiset".equals(c.getName());
            }

            @Override
            public boolean isBlacklisted(String name) {
                return "com.google.common.collect.ImmutableSortedMultiset".equals(name);
            }
        });
        try {
            XStream2 xs = new XStream2();
            xs.alias("hold", HoldRT.class);
            final CriticalXStreamException ex = assertThrows(CriticalXStreamException.class, () ->
                    xs.fromXML("<hold resolves-to=\"com.google.common.collect.ImmutableSortedMultiset\"/>"));
            assertThat(ex.getMessage(), containsString("cause-exception     : com.thoughtworks.xstream.security.ForbiddenClassException"));
            assertThat(ex.getMessage(), containsString("cause-message       : com.google.common.collect.ImmutableSortedMultiset"));
        } finally {
            ClassFilter.setDefault(ClassFilter.STANDARD);
        }
    }

    @Test
    @Issue("SECURITY-4125")
    void resolvesToAllowedTypeOnRootIsAccepted() {
        ClassFilter.setDefault(ClassFilter.NONE);
        try {
            XStream2 xs = new XStream2();
            xs.alias("hold", HoldRT.class);
            Object result = xs.fromXML("<hold resolves-to=\"" + RTProxy.class.getName() + "\"/>");
            assertThat(result, instanceOf(HoldRT.class));
        } finally {
            ClassFilter.setDefault(ClassFilter.STANDARD);
        }
    }

    @Test
    @Issue("SECURITY-4125")
    void resolvesToBlacklistedTypeIsRejectedByConverterWhenTypePermissionSuppressed() {
        // With the TypePermission escape hatch active, RobustReflectionConverter's own check
        // in instantiateNewInstance() must still block blacklisted resolves-to types.
        boolean saved = XStream2.SUPPRESS_TYPE_PERMISSION;
        ClassFilter.setDefault(new ClassFilter() {
            @Override
            public boolean isBlacklisted(Class<?> c) {
                return "com.google.common.collect.ImmutableSortedMultiset".equals(c.getName());
            }

            @Override
            public boolean isBlacklisted(String name) {
                return "com.google.common.collect.ImmutableSortedMultiset".equals(name);
            }
        });
        try {
            XStream2.SUPPRESS_TYPE_PERMISSION = true;
            XStream2 xs = new XStream2();
            xs.alias("hold", HoldRT.class);
            CriticalXStreamException ex = assertThrows(CriticalXStreamException.class, () ->
                    xs.fromXML("<hold><nested resolves-to=\"com.google.common.collect.ImmutableSortedMultiset\"/></hold>"));
            assertThat(ex.getMessage(), containsString("Refusing to instantiate resolves-to type 'com.google.common.collect.ImmutableSortedMultiset'"));
        } finally {
            XStream2.SUPPRESS_TYPE_PERMISSION = saved;
            ClassFilter.setDefault(ClassFilter.STANDARD);
        }
    }

    @Test
    @Issue("SECURITY-4125")
    void resolvesToAllowedTypeIsAcceptedWhenTypePermissionSuppressed() {
        boolean saved = XStream2.SUPPRESS_TYPE_PERMISSION;
        ClassFilter.setDefault(ClassFilter.NONE);
        try {
            XStream2.SUPPRESS_TYPE_PERMISSION = true;
            XStream2 xs = new XStream2();
            xs.alias("hold", HoldRT.class);
            HoldRT hold = (HoldRT) xs.fromXML(
                    "<hold><nested resolves-to=\"" + RTProxy.class.getName() + "\"/></hold>");
            assertThat(hold.nested, instanceOf(HoldRT.class));
        } finally {
            XStream2.SUPPRESS_TYPE_PERMISSION = saved;
            ClassFilter.setDefault(ClassFilter.STANDARD);
        }
    }

    public static class HoldRT {
        HoldRT nested;
    }

    public static class RTProxy {
        private Object readResolve() {
            return new HoldRT();
        }
    }

    /**
     * A ClassFilter that rejects XStream internal types by package prefix, simulating the
     * production ClassFilterImpl behaviour (XStream's JAR lacks Jenkins-ClassFilter-Whitelisted).
     */
    private static ClassFilter rejectXStreamInternals() {
        return new ClassFilter() {
            @Override
            public boolean isBlacklisted(Class<?> c) {
                return c.getName().startsWith("com.thoughtworks.xstream.");
            }

            @Override
            public boolean isBlacklisted(String name) {
                return name.startsWith("com.thoughtworks.xstream.");
            }
        };
    }

    @Test
    @Issue("SECURITY-4125")
    void mapWithNullValueRoundTripsWithStrictClassFilter() {
        // Mapper.Null is XStream's internal null-sentinel resolved during map deserialisation.
        // A strict ClassFilter (like the production ClassFilterImpl) would otherwise reject it.
        ClassFilter.setDefault(rejectXStreamInternals());
        try {
            XStream2 xs = new XStream2();
            xs.alias("holder", HoldList.class);

            HoldList original = new HoldList();
            original.items = new ArrayList<>();
            original.items.add(null);
            original.items.add(null);
            original.items.add(null);

            String xml = xs.toXML(original);
            HoldList restored = (HoldList) xs.fromXML(xml);
            assertThat(restored.items, hasSize(3));
            assertThat(restored.items, contains(null, null, null));
        } finally {
            ClassFilter.setDefault(ClassFilter.STANDARD);
        }
    }

    @Test
    @Issue("SECURITY-4125")
    void listWithNullValueRoundTripsWithStrictClassFilter() {
        ClassFilter.setDefault(rejectXStreamInternals());
        try {
            XStream2 xs = new XStream2();
            xs.alias("holder", HoldMap.class);

            HoldMap original = new HoldMap();
            original.items = new HashMap<>();
            original.items.put("present", "value");
            original.items.put("absent", null);

            String xml = xs.toXML(original);
            HoldMap restored = (HoldMap) xs.fromXML(xml);
            assertThat(restored.items.get("present"), equalTo("value"));
            assertTrue(restored.items.containsKey("absent"));
            assertTrue(restored.items.get("absent") == null);
        } finally {
            ClassFilter.setDefault(ClassFilter.STANDARD);
        }
    }

    @Test
    @Issue("SECURITY-4125")
    void treeMapWithNaturalOrderingRoundTripsWithStrictClassFilter() {
        // TreeMapConverter$NullComparator (extends Mapper.Null) is XStream's sentinel for a
        // TreeMap whose comparator is null (natural ordering).  A strict ClassFilter would
        // otherwise reject it during deserialisation.
        ClassFilter.setDefault(rejectXStreamInternals());
        try {
            XStream2 xs = new XStream2();
            xs.alias("holder", HoldTreeMap.class);

            HoldTreeMap original = new HoldTreeMap();
            original.items = new TreeMap<>();
            original.items.put("a", "1");
            original.items.put("b", "2");

            String xml = xs.toXML(original);
            HoldTreeMap restored = (HoldTreeMap) xs.fromXML(xml);
            assertThat(restored.items.get("a"), equalTo("1"));
            assertThat(restored.items.get("b"), equalTo("2"));
        } finally {
            ClassFilter.setDefault(ClassFilter.STANDARD);
        }
    }

    public static class HoldMap {
        Map<String, String> items;
    }

    public static class HoldTreeMap {
        TreeMap<String, String> items;
    }

    public static class HoldList {
        List<String> items;
    }
}
