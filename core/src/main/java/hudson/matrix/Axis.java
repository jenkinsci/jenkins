package hudson.matrix;

import hudson.Util;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Arrays;

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

    public Axis(String name, String... values) {
        this(name,Arrays.asList(values));        
    }

    /**
     * Used to build {@link Axis} from form.
     *
     * Axis with empty values need to be removed later.
     */
    @DataBoundConstructor
    public Axis(String name, String value) {
        this.name = name;
        this.values = new ArrayList<String>(Arrays.asList(Util.tokenize(value)));
    }

    /**
     * Returns ture if this axis is a system-reserved axis
     * that has special treatment.
     */
    public boolean isSystem() {
        return name.equals("jdk") || name.equals("label");
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
     * Used for generating the config UI.
     * If the axis is big and occupies a lot of space, use NL for separator
     * to display multi-line text
     */
    public String getValueString() {
        int len=0;
        for (String value : values)
            len += value.length();
        return Util.join(values, len>30 ?"\n":" ");
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
