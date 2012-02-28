package hudson.util;

import com.thoughtworks.xstream.converters.basic.AbstractSingleValueConverter;
import com.thoughtworks.xstream.converters.basic.StringConverter;
import org.apache.commons.collections.map.LRUMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LRUStringConverter extends AbstractSingleValueConverter {

    /**
     * A Map to store strings as long as needed to map similar strings onto the same instance and conserve memory. The
     * map can be set from the outside during construction, so it can be a LRU map or a weak map, synchronised or not.
     */
    private final Map<String,String> cache;

    public LRUStringConverter() {
        this(1000);
    }

    public LRUStringConverter(int size) {
        cache = Collections.synchronizedMap(new LRUMap(size));
    }

    public boolean canConvert(final Class type) {
        return type.equals(String.class);
    }

    public Object fromString(final String str) {
        String s = cache.get(str);

        if (s == null) {
            cache.put(str, str);
            s = str;
        }

        return s;
    }
}