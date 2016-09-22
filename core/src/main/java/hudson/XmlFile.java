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
package hudson;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.StreamException;
import com.thoughtworks.xstream.io.xml.Xpp3Driver;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.Descriptor;
import hudson.util.AtomicFileWriter;
import hudson.util.XStream2;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.Locator2;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents an XML data file that Jenkins uses as a data file.
 *
 *
 * <h2>Evolving data format</h2>
 * <p>
 * Changing data format requires a particular care so that users with
 * the old data format can migrate to the newer data format smoothly.
 *
 * <p>
 * Adding a field is the easiest. When you read an old XML that does
 * not have any data, the newly added field is left to the VM-default
 * value (if you let XStream create the object, such as
 * {@link #read()} &mdash; which is the majority), or to the value initialized by the
 * constructor (if the object is created via <tt>new</tt> and then its
 * value filled by XStream, such as {@link #unmarshal(Object)}.)
 *
 * <p>
 * Removing a field requires that you actually leave the field with
 * <tt>transient</tt> keyword. When you read the old XML, XStream
 * will set the value to this field. But when the data is saved,
 * the field will no longer will be written back to XML.
 * (It might be possible to tweak XStream so that we can simply
 * remove fields from the class. Any help appreciated.)
 *
 * <p>
 * Changing the data structure is usually a combination of the two
 * above. You'd leave the old data store with <tt>transient</tt>,
 * and then add the new data. When you are reading the old XML,
 * only the old field will be set. When you are reading the new XML,
 * only the new field will be set. You'll then need to alter the code
 * so that it will be able to correctly handle both situations,
 * and that as soon as you see data in the old field, you'll have to convert
 * that into the new data structure, so that the next <tt>save</tt> operation
 * will write the new data (otherwise you'll end up losing the data, because
 * old fields will be never written back.)
 *
 * <p>
 * You may also want to call {@link OldDataMonitor#report(UnmarshallingContext, String)}.
 * This can be done within a nested class {@code ConverterImpl} extending {@link hudson.util.XStream2.PassthruConverter}
 * in an override of {@link hudson.util.XStream2.PassthruConverter#callback}.
 *
 * <p>
 * In some limited cases (specifically when the class is the root object
 * to be read from XML, such as {@link Descriptor}), it is possible
 * to completely and drastically change the data format. See
 * {@link Descriptor#load()} for more about this technique.
 *
 * <p>
 * There's a few other possibilities, such as implementing a custom
 * {@link Converter} for XStream, or {@link XStream#alias(String, Class) registering an alias}.
 *
 * @see <a href="https://wiki.jenkins-ci.org/display/JENKINS/Architecture#Architecture-Persistence">Architecture » Persistence</a>
 * @author Kohsuke Kawaguchi
 */
public final class XmlFile {
    private final XStream xs;
    private final File file;

    public XmlFile(File file) {
        this(DEFAULT_XSTREAM,file);
    }

    public XmlFile(XStream xs, File file) {
        this.xs = xs;
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public XStream getXStream() {
        return xs;
    }

    /**
     * Loads the contents of this file into a new object.
     */
    public Object read() throws IOException {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Reading "+file);
        }
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        try {
            return xs.fromXML(in);
        } catch (XStreamException e) {
            throw new IOException("Unable to read "+file,e);
        } catch(Error e) {// mostly reflection errors
            throw new IOException("Unable to read "+file,e);
        } finally {
            in.close();
        }
    }

    /**
     * Loads the contents of this file into an existing object.
     *
     * @return
     *      The unmarshalled object. Usually the same as <tt>o</tt>, but would be different
     *      if the XML representation is completely new.
     */
    public Object unmarshal( Object o ) throws IOException {
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        try {
            // TODO: expose XStream the driver from XStream
            return xs.unmarshal(DEFAULT_DRIVER.createReader(in), o);
        } catch (XStreamException e) {
            throw new IOException("Unable to read "+file,e);
        } catch(Error e) {// mostly reflection errors
            throw new IOException("Unable to read "+file,e);
        } finally {
            in.close();
        }
    }

    public void write( Object o ) throws IOException {
        mkdirs();
        AtomicFileWriter w = new AtomicFileWriter(file);
        try {
            w.write("<?xml version='1.0' encoding='UTF-8'?>\n");
            xs.toXML(o,w);
            w.commit();
        } catch(StreamException e) {
            throw new IOException(e);
        } finally {
            w.abort();
        }
    }

    public boolean exists() {
        return file.exists();
    }

    public void delete() {
        file.delete();
    }
    
    public void mkdirs() {
        file.getParentFile().mkdirs();
    }

    @Override
    public String toString() {
        return file.toString();
    }

    /**
     * Opens a {@link Reader} that loads XML.
     * This method uses {@link #sniffEncoding() the right encoding},
     * not just the system default encoding.
     */
    public Reader readRaw() throws IOException {
        return new InputStreamReader(new FileInputStream(file),sniffEncoding());
    }

    /**
     * Returns the XML file read as a string.
     */
    public String asString() throws IOException {
        StringWriter w = new StringWriter();
        writeRawTo(w);
        return w.toString();
    }

    /**
     * Writes the raw XML to the given {@link Writer}.
     * Writer will not be closed by the implementation.
     */
    public void writeRawTo(Writer w) throws IOException {
        Reader r = readRaw();
        try {
            Util.copyStream(r,w);
        } finally {
            r.close();
        }
    }

    /**
     * Parses the beginning of the file and determines the encoding.
     *
     * @throws IOException
     *      if failed to detect encoding.
     * @return
     *      always non-null.
     */
    public String sniffEncoding() throws IOException {
        class Eureka extends SAXException {
            final String encoding;
            public Eureka(String encoding) {
                this.encoding = encoding;
            }
        }
        InputSource input = new InputSource(file.toURI().toASCIIString());
        input.setByteStream(new FileInputStream(file));

        try {
            JAXP.newSAXParser().parse(input,new DefaultHandler() {
                private Locator loc;
                @Override
                public void setDocumentLocator(Locator locator) {
                    this.loc = locator;
                }

                @Override
                public void startDocument() throws SAXException {
                    attempt();
                }

                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                    attempt();
                    // if we still haven't found it at the first start element, then we are not going to find it.
                    throw new Eureka(null);
                }

                private void attempt() throws Eureka {
                    if(loc==null)   return;
                    if (loc instanceof Locator2) {
                        Locator2 loc2 = (Locator2) loc;
                        String e = loc2.getEncoding();
                        if(e!=null)
                            throw new Eureka(e);
                    }
                }
            });
            // can't reach here
            throw new AssertionError();
        } catch (Eureka e) {
            if(e.encoding!=null)
                return e.encoding;
            // the environment can contain old version of Xerces and others that do not support Locator2
            // in such a case, assume UTF-8 rather than fail, since Jenkins internally always write XML in UTF-8
            return "UTF-8";
        } catch (SAXException e) {
            throw new IOException("Failed to detect encoding of "+file,e);
        } catch (ParserConfigurationException e) {
            throw new AssertionError(e);    // impossible
        } finally {
            // some JAXP implementations appear to leak the file handle if we just call parse(File,DefaultHandler)
            input.getByteStream().close();
        }
    }

    /**
     * {@link XStream} instance is supposed to be thread-safe.
     */
    private static final XStream DEFAULT_XSTREAM = new XStream2();

    private static final Logger LOGGER = Logger.getLogger(XmlFile.class.getName());

    private static final SAXParserFactory JAXP = SAXParserFactory.newInstance();

    private static final Xpp3Driver DEFAULT_DRIVER = new Xpp3Driver();

    static {
        JAXP.setNamespaceAware(true);
    }
}
