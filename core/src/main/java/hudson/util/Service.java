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
