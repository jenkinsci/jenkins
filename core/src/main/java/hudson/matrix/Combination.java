package hudson.matrix;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.StringTokenizer;
import java.util.HashMap;

/**
 * A particular combination of {@link Axis} values.
 *
 * For example, when axes are "x={1,2},y={3,4}", then
 * [1,3] is a combination (out of 4 possible combinations)
 *
 * @author Kohsuke Kawaguchi
 */
public final class Combination extends TreeMap<String,String> {

    public Combination(AxisList axisList, List<String> values) {
        for(int i=0; i<axisList.size(); i++)
            put(axisList.get(i).name,values.get(i));
    }

    public Combination(AxisList axisList,String... values) {
        this(axisList,Arrays.asList(values));
    }

    public Combination(Map<String,String> keyValuePairs) {
        super(keyValuePairs);
    }

    /**
     * Converts to the ID string representation:
     * <tt>axisName=value,axisName=value,...</tt>
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        for (Map.Entry<String,String> e : entrySet()) {
            if(buf.length()>0) buf.append(',');
            buf.append(e.getKey()).append('=').append(e.getValue());
        }
        return buf.toString();
    }

    /**
     * Reverse operation of {@link #toString()}.
     */
    public static Combination fromString(String id) {
        Map<String,String> m = new HashMap<String,String>();
        StringTokenizer tokens = new StringTokenizer(id, ",");
        while(tokens.hasMoreTokens()) {
            String token = tokens.nextToken();
            int idx = token.indexOf('=');
            if(idx<0)
                throw new IllegalArgumentException("Can't parse "+id);
            m.put(token.substring(0,idx),token.substring(idx+1));
        }
        return new Combination(m);
    }

    // read-only
    public void clear() {
        throw new UnsupportedOperationException();
    }

    public void putAll(Map<? extends String, ? extends String> map) {
        throw new UnsupportedOperationException();
    }

    public String put(String key, String value) {
        throw new UnsupportedOperationException();
    }

    public String remove(Object key) {
        throw new UnsupportedOperationException();
    }
}
