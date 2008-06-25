package hudson.remoting;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.MalformedURLException;

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
    public static File jarFile(Class clazz) throws IOException {
        ClassLoader cl = clazz.getClassLoader();
        if(cl==null)
            cl = ClassLoader.getSystemClassLoader();
        URL res = cl.getResource(clazz.getName().replace('.', '/') + ".class");
        if(res==null)
            throw new IllegalArgumentException("Unable to locate class file for "+clazz);
        String resURL = res.toExternalForm();
        String originalURL = resURL;
        if(resURL.startsWith("jar:"))
            return fromJarUrlToFile(resURL, 4);
        if(resURL.startsWith("wsjar:"))
            return fromJarUrlToFile(resURL, 6);

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

        throw new IllegalArgumentException(originalURL + " - " + resURL);
    }

    private static File fromJarUrlToFile(String resURL, int prefixLength) throws MalformedURLException {
        resURL = resURL.substring(prefixLength, resURL.lastIndexOf('!')); // cut off jar: and the file name portion
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
