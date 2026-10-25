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

package jenkins.util.xstream;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.ErrorWriter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.AttributeNameIterator;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.AbstractXmlReader;
import com.thoughtworks.xstream.io.xml.AbstractXmlWriter;
import com.thoughtworks.xstream.io.xml.DocumentReader;
import com.thoughtworks.xstream.io.xml.XmlFriendlyReplacer;
import hudson.RestrictedSince;
import hudson.Util;
import hudson.util.VariableResolver;
import hudson.util.XStream2;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * XML DOM like structure to preserve a portion of XStream data as-is, so that you can
 * process it later in a separate XStream call.
 *
 * <p>
 * This object captures a subset of XML infoset that XStream understands. Namely, no XML namespace,
 * no mixed content.
 *
 * <p>
 * You use it as a field in your class (that itself participates in an XStream persistence),
 * and have it receive the portion of that XML. Then later you can use {@link #unmarshal(XStream)}
 * to convert this sub-tree to an object with a possibly separate XStream instance.
 * <p>
 * The reverse operation is {@link #from(XStream, Object)} method, which marshals an object
 * into {@link XStreamDOM}.
 *
 * <p>
 * You can also use this class to parse an entire XML document into a DOM like tree with
 * {@link #from(HierarchicalStreamReader)} and {@link #writeTo(HierarchicalStreamWriter)}.
 * These two methods support variants that accept other forms.
 * <p>
 * Whereas the above methods read from and write to {@link HierarchicalStreamReader} and,
 * {@link HierarchicalStreamWriter}, we can also create {@link HierarchicalStreamReader}
 * that read from DOM and {@link HierarchicalStreamWriter} that writes to DOM. See
 * {@link #newReader()} and {@link #newWriter()} for those operations.
 *
 * <p>
 * <strong>XStreamDOM as a field of another XStream-enabled class:</strong>
 * {@link XStreamDOM} can be used as a type of a field of another class that's itself XStream-enabled,
 * such as this:
 *
 * <pre>
 * class Foo {
 *     XStreamDOM bar;
 * }
 * </pre>
 *
 * With the following XML:
 *
 * <pre>{@code
 * <foo>
 *   <bar>
 *     <payload>
 *       ...
 *     </payload>
 *   </bar>
 * </foo>
 * }</pre>
 *
 * <p>
 * The {@link XStreamDOM} object in the bar field will have the "payload" element in its tag name
 * (which means the bar element cannot have multiple children.)
 *
 * <p>
 * <strong>XStream and name escaping:</strong>
 * Because XStream wants to use letters like '$' that's not legal as a name char in XML,
 * the XML data model that it thinks of (unescaped) is actually translated into the actual
 * XML-compliant infoset via {@link XmlFriendlyReplacer}. This translation is done by
 * {@link HierarchicalStreamReader} and {@link HierarchicalStreamWriter}, transparently
 * from {@link Converter}s. In {@link XStreamDOM}, we'd like to hold the XML infoset
 * (escaped form, in XStream speak), so in our {@link ConverterImpl} we go out of the way
 * to cancel out this effect.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.473
 */
public class XStreamDOM {
    private final String tagName;

    private final String[] attributes;

    // one of them is non-null, the other is null
    private final String value;
    private final List<XStreamDOM> children;

    public XStreamDOM(String tagName, Map<String, String> attributes, String value) {
        this.tagName = tagName;
        this.attributes = toAttributeList(attributes);
        this.value = value;
        this.children = null;
    }

    public XStreamDOM(String tagName, Map<String, String> attributes, List<XStreamDOM> children) {
        this.tagName = tagName;
        this.attributes = toAttributeList(attributes);
        this.value = null;
        this.children = children;
    }

    private XStreamDOM(String tagName, String[] attributes, List<XStreamDOM> children, String value) {
        this.tagName = tagName;
        this.attributes = attributes;
        this.children = children;
        this.value = value;
    }

    private String[] toAttributeList(Map<String, String> attributes) {
        String[] r = new String[attributes.size() * 2];
        int i = 0;
        for (Map.Entry<String, String> e : attributes.entrySet()) {
            r[i++] = e.getKey();
            r[i++] = e.getValue();
        }
        return r;
    }

    public String getTagName() {
        return tagName;
    }

    /**
     * Unmarshals this DOM into an object via the given XStream.
     */
    public <T> T unmarshal(XStream xs) {
        return (T) xs.unmarshal(newReader());
    }

    public <T> T unmarshal(XStream xs, T root) {
        return (T) xs.unmarshal(newReader(), root);
    }

    /**
     * Recursively expands the variables in text and attribute values and return the new DOM.
     *
     * The expansion uses {@link Util#replaceMacro(String, VariableResolver)}, so any unresolved
     * references will be left as-is.
     */
    public XStreamDOM expandMacro(VariableResolver<String> vars) {
        String[] newAttributes = new String[attributes.length];
        for (int i = 0; i < attributes.length; i += 2) {
            //noinspection PointlessArithmeticExpression
            newAttributes[i + 0] = attributes[i]; // name
            newAttributes[i + 1] = Util.replaceMacro(attributes[i + 1], vars);
        }

        List<XStreamDOM> newChildren = null;
        if (children != null) {
            newChildren = new ArrayList<>(children.size());
            for (XStreamDOM d : children)
                newChildren.add(d.expandMacro(vars));
        }

        return new XStreamDOM(tagName, newAttributes, newChildren, Util.replaceMacro(value, vars));
    }

    public String getAttribute(String name) {
        for (int i = 0; i < attributes.length; i += 2)
            if (attributes[i].equals(name))
                return attributes[i + 1];
        return null;
    }

    public int getAttributeCount() {
        return attributes.length / 2;
    }

    String getAttributeName(int index) {
        return attributes[index * 2];
    }

    public String getAttribute(int index) {
        return attributes[index * 2 + 1];
    }

    public String getValue() {
        return value;
    }

    public List<XStreamDOM> getChildren() {
        return children;
    }

    /**
     * Returns a new {@link HierarchicalStreamReader} that reads a sub-tree rooted at this node.
     */
    public HierarchicalStreamReader newReader() {
        return new ReaderImpl(this);
    }

    /**
     * Returns a new {@link HierarchicalStreamWriter} for marshalling objects into {@link XStreamDOM}.
     * After the writer receives the calls, call {@link WriterImpl#getOutput()} to obtain the populated tree.
     */
    public static WriterImpl newWriter() {
        return new WriterImpl();
    }

    /**
     * Writes this {@link XStreamDOM} into {@link OutputStream}.
     */
    public void writeTo(OutputStream os) {
        writeTo(XStream2.getDefaultDriver().createWriter(os));
    }

    public void writeTo(Writer w) {
        writeTo(XStream2.getDefaultDriver().createWriter(w));
    }

    public void writeTo(HierarchicalStreamWriter w) {
        new ConverterImpl().marshal(this, w, null);
    }

    /**
     * Marshals the given object with the given XStream into {@link XStreamDOM} and return it.
     */
    public static XStreamDOM from(XStream xs, Object obj) {
        WriterImpl w = newWriter();
        xs.marshal(obj, w);
        return w.getOutput();
    }

    public static XStreamDOM from(InputStream in) {
        return from(XStream2.getDefaultDriver().createReader(in));
    }

    public static XStreamDOM from(Reader in) {
        return from(XStream2.getDefaultDriver().createReader(in));
    }

    public static XStreamDOM from(HierarchicalStreamReader in) {
        return new ConverterImpl().unmarshalElement(in, null);
    }

    public Map<String, String> getAttributeMap() {
        Map<String, String> r = new HashMap<>();
        for (int i = 0; i < attributes.length; i += 2)
            r.put(attributes[i], attributes[i + 1]);
        return r;
    }

    private static class ReaderImpl extends AbstractXmlReader implements DocumentReader {
        private static class Pointer {
            final XStreamDOM node;
            int pos;

            private Pointer(XStreamDOM node) {
                this.node = node;
            }

            public String peekNextChild() {
                if (hasMoreChildren())
                    return node.children.get(pos).tagName;
                return null;
            }

            public boolean hasMoreChildren() {
                return node.children != null && pos < node.children.size();
            }

            public String xpath() {
                XStreamDOM child = node.children.get(pos - 1);
                int count = 0;
                for (int i = 0; i < pos - 1; i++)
                    if (node.children.get(i).tagName.equals(child.tagName))
                        count++;
                boolean more = false;
                for (int i = pos; i < node.children.size(); i++) {
                    if (node.children.get(i).tagName.equals(child.tagName)) {
                        more = true;
                        break;
                    }
                }

                if (count == 0 && !more)  return child.tagName;   // sole child
                return child.tagName + '[' + count + ']';
            }
        }

        private final Stack<Pointer> pointers = new Stack<>();


        ReaderImpl(XStreamDOM current) {
            super(new XmlFriendlyReplacer());
            pointers.push(new Pointer(current));
        }

        private Pointer current() {
            return pointers.peek();
        }

        @Override
        public Object getCurrent() {
            return current().node;
        }

        @Override
        public boolean hasMoreChildren() {
            return current().hasMoreChildren();
        }

        @Override
        public HierarchicalStreamReader underlyingReader() {
            return this;
        }

        @Override
        public void moveDown() {
            Pointer p = current();
            pointers.push(new Pointer(p.node.children.get(p.pos++)));
        }

        @Override
        public void moveUp() {
            pointers.pop();
        }

        @Override
        public Iterator getAttributeNames() {
            return new AttributeNameIterator(this);
        }

        @Override
        public void appendErrors(ErrorWriter errorWriter) {
            StringBuilder buf = new StringBuilder();
            Pointer parent = null;
            for (Pointer cur : pointers) {
                if (parent != null) {
                    buf.append('/').append(parent.xpath());
                } else {
                    buf.append(cur.node.tagName);
                }
                parent = cur;
            }
            errorWriter.add("xpath", buf.toString());
        }

        @Override
        public void close() {
        }

        @Override
        public String peekNextChild() {
            return current().peekNextChild();
        }

        @Override
        public String getNodeName() {
            return unescapeXmlName(current().node.tagName);
        }

        @Override
        public String getValue() {
            return Util.fixNull(current().node.value);
        }

        @Override
        public String getAttribute(String name) {
            return current().node.getAttribute(name);
        }

        @Override
        public String getAttribute(int index) {
            return current().node.getAttribute(index);
        }

        @Override
        public int getAttributeCount() {
            return current().node.getAttributeCount();
        }

        @Override
        public String getAttributeName(int index) {
            return unescapeXmlName(current().node.getAttributeName(index));
        }
    }

    public static class WriterImpl extends AbstractXmlWriter {
        private static class Pending {
            final String tagName;
            List<XStreamDOM> children;
            List<String> attributes = new ArrayList<>();
            String value;

            private Pending(String tagName) {
                this.tagName = tagName;
            }

            void addChild(XStreamDOM dom) {
                if (children == null)
                    children = new ArrayList<>();
                children.add(dom);
            }

            XStreamDOM toDOM() {
                return new XStreamDOM(tagName, attributes.toArray(new String[attributes.size()]), children, value);
            }
        }

        private final Stack<Pending> pendings = new Stack<>();

        public WriterImpl() {
            pendings.push(new Pending(null));   // to get the final result
        }

        @Override
        public void startNode(String name) {
            pendings.push(new Pending(escapeXmlName(name)));
        }



        @Override
        public void endNode() {
            XStreamDOM dom = pendings.pop().toDOM();
            pendings.peek().addChild(dom);
        }

        @Override
        public void addAttribute(String name, String value) {
            List<String> atts = pendings.peek().attributes;
            atts.add(escapeXmlName(name));
            atts.add(value);
        }

        @Override
        public void setValue(String text) {
            pendings.peek().value = text;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public HierarchicalStreamWriter underlyingWriter() {
            return this;
        }

        public XStreamDOM getOutput() {
            if (pendings.size() != 1)     throw new IllegalStateException();
            return pendings.peek().children.getFirst();
        }
    }

    public static class ConverterImpl implements Converter {
        @Override
        public boolean canConvert(Class type) {
            return type == XStreamDOM.class;
        }

        /**
         * {@link XStreamDOM} holds infoset (which is 'escaped' from XStream's PoV),
         * whereas {@link HierarchicalStreamWriter} expects unescaped names,
         * so we need to unescape it first before calling into {@link HierarchicalStreamWriter}.
         */
        // TODO: ideally we'd like to use the contextual HierarchicalStreamWriter to unescape,
        // but this object isn't exposed to us
        private String unescape(String s) {
            return REPLACER.unescapeName(s);
        }

        private String escape(String s) {
            return REPLACER.escapeName(s);
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter w, MarshallingContext context) {
            XStreamDOM dom = (XStreamDOM) source;
            w.startNode(unescape(dom.tagName));
            for (int i = 0; i < dom.attributes.length; i += 2)
                w.addAttribute(unescape(dom.attributes[i]), dom.attributes[i + 1]);
            if (dom.value != null)
                w.setValue(dom.value);
            else {
                for (XStreamDOM c : Util.fixNull(dom.children)) {
                    marshal(c, w, context);
                }
            }
            w.endNode();
        }

        /**
         * Unmarshals a single child element.
         */
        @Override
        public XStreamDOM unmarshal(HierarchicalStreamReader r, UnmarshallingContext context) {
            r.moveDown();
            XStreamDOM dom = unmarshalElement(r, context);
            r.moveUp();
            return dom;
        }

        public XStreamDOM unmarshalElement(HierarchicalStreamReader r, UnmarshallingContext context) {
            String name = escape(r.getNodeName());

            int c = r.getAttributeCount();
            String[] attributes = new String[c * 2];
            for (int i = 0; i < c; i++) {
                attributes[i * 2]   = escape(r.getAttributeName(i));
                attributes[i * 2 + 1] = r.getAttribute(i);
            }

            List<XStreamDOM> children = null;
            String value = null;
            if (r.hasMoreChildren()) {
                children = new ArrayList<>();
                while (r.hasMoreChildren()) {
                    children.add(unmarshal(r, context));
                }
            } else {
                value = r.getValue();
            }

            return new XStreamDOM(name, attributes, children, value);
        }
    }

    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.301")
    public static final XmlFriendlyReplacer REPLACER = new XmlFriendlyReplacer();
}
