package hudson.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Load classes by looking up <tt>META-INF/services</tt>.
 *
 * @author Kohsuke Kawaguchi
 */
public class Service {
    /**
     * Look up <tt>META-INF/service/<i>SPICLASSNAME</i></tt> from the classloader
     * and all the discovered classes into the given collection.
     */
    public static <T> void load(Class<T> spi, ClassLoader cl, Collection<Class<? extends T>> result) {
        try {
            Enumeration<URL> e = cl.getResources("META-INF/services/" + spi.getName());
            while(e.hasMoreElements()) {
                BufferedReader r = null;
                URL url = e.nextElement();
                try {
                    r = new BufferedReader(new InputStreamReader(url.openStream(),"UTF-8"));
                    String line;
                    while((line=r.readLine())!=null) {
                        if(line.startsWith("#"))
                            continue;   // comment line
                        line = line.trim();
                        if(line.length()==0)
                            continue;   // empty line. ignore.

                        try {
                            result.add(cl.loadClass(line).asSubclass(spi));
                        } catch (ClassNotFoundException x) {
                            LOGGER.log(Level.WARNING, "Failed to load "+line, x);
                        }
                    }
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, "Failed to load "+url, x);
                } finally {
                    r.close();
                }
            }
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, "Failed to look up service providers for "+spi, x);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Service.class.getName());
}
