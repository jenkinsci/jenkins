/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Ullrich Hafner
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
package hudson.remoting;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.InputStream;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.URLConnection;
import java.net.JarURLConnection;
import java.lang.reflect.Field;
import java.util.zip.ZipFile;

/**
 * Locates where a given class is loaded from.
 *
 * @author Kohsuke Kawaguchi
 */
public class Which {
    /**
     * Locates the jar file that contains the given class.
     *
     * @throws IllegalArgumentException
     *      if failed to determine.
     */
    public static URL jarURL(Class clazz) throws IOException {
        ClassLoader cl = clazz.getClassLoader();
        if(cl==null)
            cl = ClassLoader.getSystemClassLoader();
        URL res = cl.getResource(clazz.getName().replace('.', '/') + ".class");
        if(res==null)
            throw new IllegalArgumentException("Unable to locate class file for "+clazz);
        return res;
    }

    /**
     * Locates the jar file that contains the given class.
     *
     * <p>
     * Note that jar files are not always loaded from {@link File},
     * so for diagnostics purposes {@link #jarURL(Class)} is preferrable.
     *
     * @throws IllegalArgumentException
     *      if failed to determine.
     */
    public static File jarFile(Class clazz) throws IOException {
        URL res = jarURL(clazz);
        String resURL = res.toExternalForm();
        String originalURL = resURL;
        if(resURL.startsWith("jar:"))
            return fromJarUrlToFile(resURL);
        if(resURL.startsWith("wsjar:"))
            return fromJarUrlToFile(resURL);

        if(resURL.startsWith("code-source:/")) {
            // OC4J apparently uses this. See http://www.nabble.com/Hudson-on-OC4J-tt16702113.html
            resURL = resURL.substring("code-source:/".length(), resURL.lastIndexOf('!')); // cut off jar: and the file name portion
            return new File(decode(new URL("file:/"+resURL).getPath()));
        }

        if(resURL.startsWith("file:")) {
            // unpackaged classes
            int n = clazz.getName().split("\\.").length; // how many slashes do wo need to cut?
            for( ; n>0; n-- ) {
                int idx = Math.max(resURL.lastIndexOf('/'), resURL.lastIndexOf('\\'));
                if(idx<0)   throw new IllegalArgumentException(originalURL + " - " + resURL);
                resURL = resURL.substring(0,idx);
            }

            // won't work if res URL contains ' '
            // return new File(new URI(null,new URL(res).toExternalForm(),null));
            // won't work if res URL contains '%20'
            // return new File(new URL(res).toURI());

            return new File(decode(new URL(resURL).getPath()));
        }

        if(resURL.startsWith("vfszip:")) {
            // JBoss5
            InputStream is = res.openStream();
            try {
                Field f = is.getClass().getDeclaredField("delegate");
                f.setAccessible(true);
                Object delegate = f.get(is);
                f = delegate.getClass().getDeclaredField("this$0");
                f.setAccessible(true);
                ZipFile zipFile = (ZipFile)f.get(delegate);
                return new File(zipFile.getName());
            } catch (NoSuchFieldException e) {
                // something must have changed in JBoss5. fall through
            } catch (IllegalAccessException e) {
                // something must have changed in JBoss5. fall through
            } finally {
                is.close();
            }

        }

        URLConnection con = res.openConnection();
        if (con instanceof JarURLConnection) {
            JarURLConnection jcon = (JarURLConnection) con;
            String n = jcon.getJarFile().getName();
            if(n.length()>0) {// JDK6u10 needs this
                return new File(n);
            }
        }

        throw new IllegalArgumentException(originalURL + " - " + resURL);
    }

    public static File jarFile(URL resource) throws IOException {
        return fromJarUrlToFile(resource.toExternalForm());
    }

    private static File fromJarUrlToFile(String resURL) throws MalformedURLException {
        resURL = resURL.substring(resURL.indexOf(':')+1, resURL.lastIndexOf('!')); // cut off "scheme:" and the file name portion
        return new File(decode(new URL(resURL).getPath()));
    }

    /**
     * Decode '%HH'.
     */
    private static String decode(String s) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for( int i=0; i<s.length();i++ ) {
            char ch = s.charAt(i);
            if(ch=='%') {
                baos.write(hexToInt(s.charAt(i+1))*16 + hexToInt(s.charAt(i+2)));
                i+=2;
                continue;
            }
            baos.write(ch);
        }
        try {
            return new String(baos.toByteArray(),"UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new Error(e); // impossible
        }
    }

    private static int hexToInt(int ch) {
        return Character.getNumericValue(ch);
    }
}
