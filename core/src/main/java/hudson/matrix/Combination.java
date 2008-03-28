package hudson.matrix;

import hudson.Util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * A particular combination of {@link Axis} values.
 *
 * For example, when axes are "x={1,2},y={3,4}", then
 * [x=1,y=3] is a combination (out of 4 possible combinations)
 *
 * @author Kohsuke Kawaguchi
 */
public final class Combination extends TreeMap<String,String> implements Comparable<Combination> {

    public Combination(AxisList axisList, List<String> values) {
        for(int i=0; i<axisList.size(); i++)
            super.put(axisList.get(i).name,values.get(i));
    }

    public Combination(AxisList axisList,String... values) {
        this(axisList,Arrays.asList(values));
    }

    public Combination(Map<String,String> keyValuePairs) {
        for (Map.Entry<String, String> e : keyValuePairs.entrySet())
            super.put(e.getKey(),e.getValue());
    }

    public int compareTo(Combination that) {
        int d = this.size()-that.size();
        if(d!=0)    return d;

        Iterator<Map.Entry<String,String>> itr = this.entrySet().iterator();
        Iterator<Map.Entry<String,String>> jtr = that.entrySet().iterator();
        while(itr.hasNext()) {
            Map.Entry<String,String> i = itr.next();
            Map.Entry<String,String> j = jtr.next();

            d = i.getKey().compareTo(j.getKey());
            if(d!=0)    return d;
            d = i.getValue().compareTo(j.getValue());
            if(d!=0)    return d;
        }
        return 0;
    }

    /**
     * Works like {@link #toString()} but only include the given axes.
     */
    public String toString(Collection<Axis> subset) {
        if(size()==1 && subset.size()==1)
            return values().iterator().next();

        StringBuilder buf = new StringBuilder();
        for (Axis a : subset) {
            if(buf.length()>0) buf.append(',');
            buf.append(a.name).append('=').append(get(a.name));
        }
        if(buf.length()==0) buf.append("default"); // special case to avoid 0-length name.
        return buf.toString();
    }
    
    /**
     * Converts to the ID string representation:
     * <tt>axisName=value,axisName=value,...</tt>
     *
     * @param sep1
     *      The separator between multiple axes.
     * @param sep2
     *      The separator between axis name and value.
     */
    public String toString(char sep1, char sep2) {
        StringBuilder buf = new StringBuilder();
        for (Map.Entry<String,String> e : entrySet()) {
            if(buf.length()>0) buf.append(sep1);
            buf.append(e.getKey()).append(sep2).append(e.getValue());
        }
        if(buf.length()==0) buf.append("default"); // special case to avoid 0-length name.
        return buf.toString();
    }

    public String toString() {
        return toString(',','=');
    }

    /**
     * Gets the 8 character-wide hash code for this combination
     */
    public String digest() {
        return Util.getDigestOf(toString());
    }

    /**
     * Reverse operation of {@link #toString()}.
     */
    public static Combination fromString(String id) {
        if(id.equals("default"))
            return new Combination(Collections.<String,String>emptyMap());

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

    /**
     * Creates compact string representataion suitable for display purpose.
     *
     * <p>
     * The string is made compact by looking for {@link Axis} whose values
     * are unique, and omit the axis name.
     */
    public String toCompactString(AxisList axes) {
        Set<String> nonUniqueAxes = new HashSet<String>();
        Map<String,Axis> axisByValue = new HashMap<String,Axis>();

        for (Axis a : axes) {
            for (String v : a.values) {
                Axis old = axisByValue.put(v,a);
                if(old!=null) {
                    // these two axes have colliding values
                    nonUniqueAxes.add(old.name);
                    nonUniqueAxes.add(a.name);
                }
            }
        }

        StringBuilder buf = new StringBuilder();
        for (Map.Entry<String,String> e : entrySet()) {
            if(buf.length()>0) buf.append(',');
            if(nonUniqueAxes.contains(e.getKey()))
                buf.append(e.getKey()).append('=');
            buf.append(e.getValue());
        }
        if(buf.length()==0) buf.append("default"); // special case to avoid 0-length name.
        return buf.toString();
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
