/*
 * The MIT License
 *
 * Copyright (c) 2012, CloudBees, Inc.
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

package jenkins.util;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.io.Serializable;
import java.util.Map;

/**
 * {@link TreeString} is an alternative string representation that saves the
 * memory when you have a large number of strings that share common prefixes
 * (such as various file names.)
 * <p>
 * {@link TreeString} can be built with {@link TreeStringBuilder}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.473
 */
public final class TreeString implements Serializable {
    private static final long serialVersionUID = 3621959682117480904L;

    /**
     * Parent node that represents the prefix.
     */
    private TreeString parent;

    /**
     * {@link #parent}+{@link #label} is the string value of this node.
     */
    private char[] label;

    /**
     * Creates a new root {@link TreeString}
     */
    /* package */TreeString() {
        this(null, "");
    }

    /* package */TreeString(final TreeString parent, final String label) {
        assert parent == null || !label.isEmpty(); // if there's a parent,
                                                     // label can't be empty.

        this.parent = parent;
        this.label = label.toCharArray(); // string created as a substring of
                                          // another string can have a lot of
                                          // garbage attached to it.
    }

    /* package */String getLabel() {
        return new String(label);
    }

    /**
     * Inserts a new node between this node and its parent, and returns the
     * newly inserted node.
     * <p>
     * This operation doesn't change the string representation of this node.
     */
    /* package */TreeString split(final String prefix) {
        assert getLabel().startsWith(prefix);
        char[] suffix = new char[label.length - prefix.length()];
        System.arraycopy(label, prefix.length(), suffix, 0, suffix.length);

        TreeString middle = new TreeString(parent, prefix);
        label = suffix;
        parent = middle;

        return middle;
    }

    /**
     * How many nodes do we have from the root to this node (including 'this'
     * itself?) Thus depth of the root node is 1.
     */
    private int depth() {
        int i = 0;
        for (TreeString p = this; p != null; p = p.parent) {
            i++;
        }
        return i;
    }

    @Override
    public boolean equals(final Object rhs) {
        if (rhs == null) {
            return false;
        }
        return rhs.getClass() == TreeString.class
                && ((TreeString) rhs).getLabel().equals(getLabel());
    }

    @Override
    public int hashCode() {
        int h = parent == null ? 0 : parent.hashCode();

        for (char c : label) {
            h = 31 * h + c;
        }

        assert toString().hashCode() == h;
        return h;
    }

    /**
     * Returns the full string representation.
     */
    @Override
    public String toString() {
        char[][] tokens = new char[depth()][];
        int i = tokens.length;
        int sz = 0;
        for (TreeString p = this; p != null; p = p.parent) {
            tokens[--i] = p.label;
            sz += p.label.length;
        }

        StringBuilder buf = new StringBuilder(sz);
        for (char[] token : tokens) {
            buf.append(token);
        }

        return buf.toString();
    }

    /**
     * Interns {@link #label}
     */
    /* package */void dedup(final Map<String, char[]> table) {
        String l = getLabel();
        char[] v = table.get(l);
        if (v != null) {
            label = v;
        }
        else {
            table.put(l, label);
        }
    }

    public boolean isBlank() {
        String string = toString();
        return string == null || string.isBlank();
    }

    public static String toString(final TreeString t) {
        return t == null ? null : t.toString();
    }

    /**
     * Creates a {@link TreeString}. Useful if you need to create one-off
     * {@link TreeString} without {@link TreeStringBuilder}. Memory consumption
     * is still about the same to {@code new String(s)}.
     *
     * @return null if the parameter is null
     */
    public static TreeString of(final String s) {
        if (s == null) {
            return null;
        }
        return new TreeString(null, s);
    }

    /**
     * Default {@link Converter} implementation for XStream that does interning
     * scoped to one unmarshalling.
     */
    public static final class ConverterImpl implements Converter {
        public ConverterImpl(final XStream xs) {}

        @Override
        public void marshal(final Object source, final HierarchicalStreamWriter writer,
                final MarshallingContext context) {
            writer.setValue(source == null ? null : source.toString());
        }

        @Override
        public Object unmarshal(final HierarchicalStreamReader reader, final UnmarshallingContext context) {
            TreeStringBuilder builder = (TreeStringBuilder) context.get(TreeStringBuilder.class);
            if (builder == null) {
                context.put(TreeStringBuilder.class, builder = new TreeStringBuilder());

                // dedup at the end
                final TreeStringBuilder _builder = builder;
                context.addCompletionCallback(_builder::dedup, 0);
            }
            return builder.intern(reader.getValue());
        }

        @Override
        public boolean canConvert(final Class type) {
            return type == TreeString.class;
        }
    }
}
