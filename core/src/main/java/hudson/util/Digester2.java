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
package hudson.util;

import jenkins.util.SystemProperties;
import org.apache.commons.digester.Digester;
import org.apache.commons.digester.Rule;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Digester} wrapper to fix the issue DIGESTER-118.
 * Since Jenkins 2.TODO, this class also attempts to set secure parsing defaults to prevent XXE (XML External Entity) vulnerabilities.
 * <ul>
 *     <li>{@link #Digester2()} and {@link #Digester2(XMLReader)} will apply XXE protections unless a system property is set to disable them.</li>
 *     <li>{@link #Digester2(boolean)} and {@link #Digester2(XMLReader, boolean)} will apply XXE protections if and only if the boolean argument is true.</li>
 *     <li>{@link #Digester2(SAXParser)} will <strong>not</strong> apply protections, whatever instantiated the {@code SAXParser} should do that.</li>
 * </ul>
 *
 * @author Kohsuke Kawaguchi
 * @since 1.125
 */
// TODO deprecate and possibly restrict in a subsequent weekly release
public class Digester2 extends Digester {

    private static final Logger LOGGER = Logger.getLogger(Digester2.class.getName());

    public Digester2() {
        if (shouldConfigureSecurely()) {
            configureSecurely(this);
        }
    }

    /**
     * Callers need to configure the {@link SAXParser} securely if processing potentially untrusted input, as this does not do it automatically (unlike other constructors).
     * @param parser the parser
     */
    @Deprecated
    public Digester2(SAXParser parser) {
        super(parser);
    }

    public Digester2(XMLReader reader) {
        super(reader);
        if (shouldConfigureSecurely()) {
            configureSecurely(reader);
        }
    }

    /**
     * @param processSecurely true iff this should configure the parser to prevent XXE.
     * @since 2.275 and 2.263.2
     */
    public Digester2(boolean processSecurely) {
        if (processSecurely) {
            configureSecurely(this);
        }
    }

    /**
     * @param reader the reader
     * @param processSecurely true iff this should configure the parser to prevent XXE.
     * @since 2.275 and 2.263.2
     */
    public Digester2(XMLReader reader, boolean processSecurely) {
        super(reader);
        if (processSecurely) {
            configureSecurely(reader);
        }
    }

    private boolean shouldConfigureSecurely() {
        return !SystemProperties.getBoolean(Digester2.class.getName() + ".UNSAFE");
    }

    private static void configureSecurely(Digester digester) {
        digester.setXIncludeAware(false);
        digester.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        digester.setNamespaceAware(false);
        for (Map.Entry<String, Boolean> entry : FEATURES.entrySet()) {
            try {
                digester.setFeature(entry.getKey(), entry.getValue());
            } catch (ParserConfigurationException|SAXException ex) {
                LOGGER.log(Level.WARNING, "Failed to securely configure Digester2 instance", ex);
            }
        }
    }

    private static void configureSecurely(XMLReader reader) {
        reader.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        for (Map.Entry<String, Boolean> entry : FEATURES.entrySet()) {
            try {
                reader.setFeature(entry.getKey(), entry.getValue());
            } catch (SAXException ex) {
                LOGGER.log(Level.WARNING, "Failed to securely configure Digester2/XMLReader instance", ex);
            }
        }
    }

    // TODO JDK 9+: Use Map.of instead
    private static final Map<String, Boolean> FEATURES = new HashMap<>();
    static {
        FEATURES.put("http://apache.org/xml/features/disallow-doctype-decl", true);
        FEATURES.put("http://xml.org/sax/features/external-general-entities", false);
        FEATURES.put("http://xml.org/sax/features/external-parameter-entities", false);
        FEATURES.put("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    }

    @Override
    public void addObjectCreate(String pattern, Class clazz) {
        addRule(pattern,new ObjectCreateRule2(clazz));
    }

    private static final class ObjectCreateRule2 extends Rule {
        private final Class clazz;
        
        public ObjectCreateRule2(Class clazz) {
            this.clazz = clazz;
        }

        @Override
        public void begin(String namespace, String name, Attributes attributes) throws Exception {
            Object instance = clazz.newInstance();
            digester.push(instance);
        }

        @Override
        public void end(String namespace, String name) throws Exception {
            digester.pop();
        }
    }
}
