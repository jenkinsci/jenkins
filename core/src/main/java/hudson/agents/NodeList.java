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

package hudson.agents;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Node;
import hudson.util.RobustCollectionConverter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link CopyOnWriteArrayList} for {@link Node} that has special serialization semantics
 * of not serializing {@link EphemeralNode}s.
 *
 * @author Kohsuke Kawaguchi
 */
public final class NodeList extends ArrayList<Node> {

    private Map<String, Node> map = new HashMap<>();

    public NodeList() {
    }

    public NodeList(Collection<? extends Node> c) {
        super(c);
        for (Node node : c) {
            if (map.put(node.getNodeName(), node) != null) {
                // make sure that all names are unique
                throw new IllegalArgumentException(node.getNodeName() + " is defined more than once");
            }
        }
    }

    public NodeList(Node... toCopyIn) {
        this(Arrays.asList(toCopyIn));
    }

    public @CheckForNull Node getNode(String nodeName) {
        return map.get(nodeName);
    }


    @Override
    public void add(int index, Node element) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public Node remove(int index) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public boolean addAll(Collection<? extends Node> c) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public boolean addAll(int index, Collection<? extends Node> c) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public boolean add(Node node) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public Node set(int index, Node element) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    /**
     * {@link Converter} implementation for XStream.
     *
     * Serialization form is compatible with plain {@link List}.
     */
    public static final class ConverterImpl extends RobustCollectionConverter {
        public ConverterImpl(XStream xstream) {
            super(xstream);
        }

        @Override
        public boolean canConvert(Class type) {
            return type == NodeList.class;
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            for (Node o : (NodeList) source) {
                if (o instanceof EphemeralNode)
                    continue;   // skip
                writeItem(o, context, writer);
            }
        }

        @Override
        protected Object createCollection(Class type) {
            return new ArrayList();
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            return new NodeList((List<Node>) super.unmarshal(reader, context));
        }
    }
}
