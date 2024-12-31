package jenkins.util;

import static org.junit.Assert.assertArrayEquals;

import net.sf.json.JSONArray;
import org.junit.Test;

public class PluginLabelUtilTest {

    @Test
    public void testCanonicalLabels() {
        JSONArray labels = new JSONArray();
        labels.add("agents");
        labels.add("api-plugin");
        labels.add("library");
        assertArrayEquals(new String[]{"agent", "api-plugin"},
                PluginLabelUtil.canonicalLabels(labels));
    }
}
