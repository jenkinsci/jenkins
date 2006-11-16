package hudson;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.io.StreamException;
import com.thoughtworks.xstream.io.xml.XppReader;
import hudson.util.AtomicFileWriter;
import hudson.util.IOException2;
import hudson.util.XStream2;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * Represents an XML data file that Hudson uses as a data file.
 *
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

    /**
     * Loads the contents of this file into a new object.
     */
    public Object read() throws IOException {
        Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            return xs.fromXML(r);
        } catch(StreamException e) {
            throw new IOException2("Unable to read "+file,e);
        } catch(ConversionException e) {
            throw new IOException2("Unable to read "+file,e);
        } finally {
            r.close();
        }
    }

    /**
     * Loads the contents of this file into an existing object.
     *
     * @return
     *      The unmarshalled object. Usually the same as <tt>o</tt>, but would be different
     *      if the XML representation if completely new.
     */
    public Object unmarshal( Object o ) throws IOException {
        Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"));
        try {
            return xs.unmarshal(new XppReader(r),o);
        } catch (StreamException e) {
            throw new IOException2(e);
        } catch(ConversionException e) {
            throw new IOException2("Unable to read "+file,e);
        } finally {
            r.close();
        }
    }

    public void write( Object o ) throws IOException {
        AtomicFileWriter w = new AtomicFileWriter(file);
        try {
            w.write("<?xml version='1.0' encoding='UTF-8'?>\n");
            xs.toXML(o,w);
            w.commit();
        } catch(StreamException e) {
            throw new IOException2(e);
        } finally {
            w.close();
        }
    }

    public boolean exists() {
        return file.exists();
    }

    public void mkdirs() {
        file.getParentFile().mkdirs();
    }

    public String toString() {
        return file.toString();
    }

    /**
     * {@link XStream} instance is supposed to be thread-safe.
     */
    private static final XStream DEFAULT_XSTREAM = new XStream2();
}
