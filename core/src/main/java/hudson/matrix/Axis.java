package hudson.matrix;

import hudson.Util;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

/**
 * Configuration axis.
 *
 * <p>
 * This class represents a single dimension of the configuration matrix.
 * For example, the JAX-WS RI test configuration might include
 * one axis "container={glassfish,tomcat,jetty}" and another axis
 * "stax={sjsxp,woodstox}", and so on.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Axis implements Comparable<Axis>, Iterable<String> {
    /**
     * Name of this axis.
     * Used as a variable name.
     */
    public final String name;

    /**
     * Possible values for this axis.
     */
    public final List<String> values;

    public Axis(String name, List<String> values) {
        this.name = name;
        this.values = new ArrayList<String>(values);
        if(values.isEmpty())
            throw new IllegalArgumentException(); // bug in the code
    }

    public Iterator<String> iterator() {
        return values.iterator();
    }

    public int size() {
        return values.size();
    }

    public String value(int index) {
        return values.get(index);
    }

    /**
     * Axis is fully ordered so that we can convert between a list of axis
     * and a string unambiguously.
     */
    public int compareTo(Axis that) {
        return this.name.compareTo(that.name);
    }

    public String toString() {
        return new StringBuilder().append(name).append("={").append(Util.join(values,",")).append('}').toString();
    }

    /**
     * Parses the submitted form (where possible values are
     * presented as a list of checkboxes) and creates an axis
     */
    public static Axis parsePrefixed(StaplerRequest req, String name) {
        List<String> values = new ArrayList<String>();
        String prefix = name+'.';

        Enumeration e = req.getParameterNames();
        while (e.hasMoreElements()) {
            String paramName = (String) e.nextElement();
            if(paramName.startsWith(prefix))
                values.add(paramName.substring(prefix.length()));
        }
        if(values.isEmpty())
            return null;
        return new Axis(name,values);
    }
}
