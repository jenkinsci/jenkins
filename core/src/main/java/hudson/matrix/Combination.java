/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.matrix;

import hudson.Util;

import java.util.ArrayList;
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
import static java.lang.Boolean.TRUE;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;

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
            super.put(axisList.get(i).getName(),values.get(i));
    }

    public Combination(AxisList axisList,String... values) {
        this(axisList,Arrays.asList(values));
    }

    public Combination(Map<String,String> keyValuePairs) {
        for (Map.Entry<String, String> e : keyValuePairs.entrySet())
            super.put(e.getKey(),e.getValue());
    }

    public String get(Axis a) {
        return get(a.getName());
    }

    /**
     * Obtains the continuous unique index number of this {@link Combination}
     * in the given {@link AxisList}.
     */
    public int toIndex(AxisList axis) {
        int r = 0;
        for (Axis a : axis) {
            r *= a.size();
            r += a.indexOf(get(a));
        }
        return r;
    }

    /**
     * Obtains a number N such that "N%M==0" would create
     * a reasonable sparse matrix for integer M.
     *
     * <p>
     * This is bit like {@link #toIndex(AxisList)}, but instead
     * of creating a continuous number (which often maps different
     * values of the same axis to the same index in modulo N residue ring,
     * we use a prime number P as the base. I think this guarantees the uniform
     * distribution in any N smaller than 2P (but proof, anyone?)
     */
    private long toModuloIndex(AxisList axis) {
        long r = 0;
        for (Axis a : axis) {
            r += a.indexOf(get(a));
            r *= 31;
        }
        return r;
    }

    /**
     * Evaluates the given Groovy expression with values bound from this combination.
     *
     * <p>
     * For example, if this combination is a=X,b=Y, then expressions like <tt>a=="X"</tt> would evaluate to
     * true.
     */
    public boolean evalGroovyExpression(AxisList axes, String expression) {

        return evalGroovyExpression(axes, expression, new Binding());
    }

    /**
     * @see #evalGroovyExpression(AxisList, String)
     * @since 1.515
     */
    public boolean evalGroovyExpression(AxisList axes, String expression, Binding binding) {
        if(Util.fixEmptyAndTrim(expression)==null)
            return true;

        for (Map.Entry<String, String> e : entrySet())
            binding.setVariable(e.getKey(),e.getValue());

        binding.setVariable("index",toModuloIndex(axes));
        binding.setVariable("uniqueId",toIndex(axes));

        GroovyShell shell = new GroovyShell(binding);

        Object result = shell.evaluate("use("+BooleanCategory.class.getName().replace('$','.')+") {"+expression+"}");
        return TRUE.equals(result);
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
            buf.append(a.getName()).append('=').append(get(a));
        }
        if(buf.length()==0) buf.append("default"); // special case to avoid 0-length name.
        return buf.toString();
    }

    /**
     * Gets the values that correspond to the specified axes, in their order.
     */
    public List<String> values(Collection<? extends Axis> axes) {
        List<String> r = new ArrayList<String>(axes.size());
        for (Axis a : axes)
            r.add(get(a));
        return r;
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

    @Override
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
     * Creates compact string representation suitable for display purpose.
     *
     * <p>
     * The string is made compact by looking for {@link Axis} whose values
     * are unique, and omit the axis name.
     */
    public String toCompactString(AxisList axes) {
        Set<String> nonUniqueAxes = new HashSet<String>();
        Map<String,Axis> axisByValue = new HashMap<String,Axis>();

        for (Axis a : axes) {
            for (String v : a.getValues()) {
                Axis old = axisByValue.put(v,a);
                if(old!=null) {
                    // these two axes have colliding values
                    nonUniqueAxes.add(old.getName());
                    nonUniqueAxes.add(a.getName());
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
    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> map) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String put(String key, String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String remove(Object key) {
        throw new UnsupportedOperationException();
    }

    /**
     * Duck-typing for boolean expressions.
     *
     * @see Combination#evalGroovyExpression(AxisList,String)
     */
    public static final class BooleanCategory {
        /**
         * x -> y
         */
        public static Boolean implies(Boolean lhs, Boolean rhs) {
            return !lhs || rhs;
        }
    }
}
