package hudson.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import hudson.model.Describable;
import hudson.model.Descriptor;
import org.junit.jupiter.api.Test;

class PersistedListTest {
    @Test
    void associatedConverterUsed() {
        XStream2 xstream = new XStream2();

        final Data data = new Data();
        data.strings = new PersistedList<>();
        data.strings.add("foo");
        final String xml = xstream.toXML(data);

        assertThat(xml, allOf(not(containsString("<data>")), not(containsString("</data>")), not(containsString("<owner"))));

        String craftedXml = xml.replace("<string>foo</string>", "<int>42</int>");
        assertThat(xml, not(equalTo(craftedXml)));

        final Object o = xstream.fromXML(craftedXml);
        assertThat(o, instanceOf(Data.class));
        final PersistedList<String> list = ((Data) o).strings;
        assertThat(list.getClass(), is(PersistedList.class));
        assertThat(list, empty());
    }

    // Arguably not the "expected" behavior.
    @Test
    void defaultSerializationForSubclass() {
        XStream2 xstream = new XStream2();

        final Data data = new Data();
        data.strings = new PersistedListSubtype<>();
        data.strings.add("foo");
        final String xml = xstream.toXML(data);

        assertThat(xml, allOf(containsString("<data>"), containsString("</data>"), containsString("<owner")));
        {
            final Object o = xstream.fromXML(xml);
            assertThat(o, instanceOf(Data.class));
            final PersistedList<String> list = ((Data) o).strings;
            assertThat(list, instanceOf(PersistedListSubtype.class));
            assertThat(list, not(empty()));
        }

        String craftedXml = xml.replace("<string>foo</string>", "<int>42</int>");
        assertThat(xml, not(equalTo(craftedXml)));

        final Object o = xstream.fromXML(craftedXml);
        assertThat(o, instanceOf(Data.class));
        final PersistedList<String> list = ((Data) o).strings;
        assertThat(list, instanceOf(PersistedListSubtype.class));
        assertThat(list, contains(instanceOf(Integer.class))); // TODO FIXME this seems wrong
    }

    @Test
    void deserializeSubtypeDefaultConverter() {
        XStream2 xstream = new XStream2();

        final String serialized = """
                <hudson.util.PersistedListTest_-Data>
                  <strings class="hudson.util.PersistedListTest$PersistedListSubtype">
                    <data>
                      <string>foo</string>
                    </data>
                    <owner class="null"/>
                  </strings>
                </hudson.util.PersistedListTest_-Data>""";
        final Object fromXML = xstream.fromXML(serialized);
        assertThat(fromXML, instanceOf(Data.class));
        assertThat(((Data) fromXML).strings, contains("foo"));
    }

    @Test
    void deserializeSubtypeDefaultConverterWrongType() {
        XStream2 xstream = new XStream2();

        final String serialized = """
                <hudson.util.PersistedListTest_-Data>
                  <strings class="hudson.util.PersistedListTest$PersistedListSubtype">
                    <data>
                      <int>12</int>
                    </data>
                    <owner class="null"/>
                  </strings>
                </hudson.util.PersistedListTest_-Data>""";
        final Object fromXML = xstream.fromXML(serialized);
        assertThat(fromXML, instanceOf(Data.class));
        final PersistedList<String> strings = ((Data) fromXML).strings;
        assertThat(strings, contains(instanceOf(Integer.class))); // TODO FIXME this seems wrong
    }

    @Test
    void deserializeCustomConverter() {
        XStream2 xstream = new XStream2();

        final String serialized = """
                <hudson.util.PersistedListTest_-Data>
                  <strings>
                    <string>foo</string>
                  </strings>
                </hudson.util.PersistedListTest_-Data>""";
        final Object fromXML = xstream.fromXML(serialized);
        assertThat(fromXML, instanceOf(Data.class));
        assertThat(((Data) fromXML).strings, contains("foo"));
    }

    @Test
    void deserializeCustomConverterWrongType() {
        XStream2 xstream = new XStream2();

        final String serialized = """
                <hudson.util.PersistedListTest_-Data>
                  <strings>
                    <int>12</int>
                  </strings>
                </hudson.util.PersistedListTest_-Data>""";
        final Object fromXML = xstream.fromXML(serialized);
        assertThat(fromXML, instanceOf(Data.class));
        assertThat(((Data) fromXML).strings, empty());
    }

    private static class PersistedListSubtype<T, E> extends PersistedList<E> {
        T getThing() {
            return null;
        }
    }

    private static class Data {
        private PersistedList<String> strings;
    }

    // TODO Test with subtyped field
    private static class SubtypeData {
        private PersistedListSubtype<Object, String> strings;
    }

    public static class MyDescribable implements Describable<MyDescribable> {
        public static class DescriptorImpl extends Descriptor<MyDescribable> {
        }
    }

    public static class MyOtherDescribable implements Describable<MyOtherDescribable> {
        public static class DescriptorImpl extends Descriptor<MyOtherDescribable> {
        }
    }
}
