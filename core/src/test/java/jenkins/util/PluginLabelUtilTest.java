package jenkins.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import net.sf.json.JSONArray;
import org.junit.jupiter.api.Test;

class PluginLabelUtilTest {

    @Test
    void testCanonicalLabels() {
        JSONArray labels = new JSONArray();
        labels.add("slaves");
        labels.add("api-plugin");
        labels.add("library");
        assertArrayEquals(new String[]{"agent", "api-plugin"},
                PluginLabelUtil.canonicalLabels(labels));
    }
}
