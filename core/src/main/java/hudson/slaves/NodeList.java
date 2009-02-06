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
package hudson.slaves;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.model.Node;
import hudson.util.RobustCollectionConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link CopyOnWriteArrayList} for {@link Node} that has special serialization semantics
 * of not serializing {@link EphemeralNode}s.
 *
 * @author Kohsuke Kawaguchi
 */
public final class NodeList extends CopyOnWriteArrayList<Node> {
    public NodeList() {
    }

    public NodeList(Collection<? extends Node> c) {
        super(c);
    }

    public NodeList(Node[] toCopyIn) {
        super(toCopyIn);
    }

    /**
     * {@link Converter} implementation for XStream.
     *
     * Serializaion form is compatible with plain {@link List}.
     */
    public static final class ConverterImpl extends RobustCollectionConverter {
        public ConverterImpl(XStream xstream) {
            super(xstream);
        }

        public boolean canConvert(Class type) {
            return type==NodeList.class;
        }

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            for (Node o : (NodeList) source) {
                if(o instanceof EphemeralNode)
                    continue;   // skip
                writeItem(o, context, writer);
            }
        }

        protected Object createCollection(Class type) {
            return new ArrayList();
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            return new NodeList((List<Node>)super.unmarshal(reader, context));
        }
    }
}
