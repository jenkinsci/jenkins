package hudson.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;

/**
 * Poorman's clone of JDK6 ServiceLoader.
 * 
 * @author Kohsuke Kawaguchi
 */
public class ServiceLoader {
    public static <T> List<T> load(ClassLoader classLoader, Class<T> type) throws IOException {
        List<T> result = new ArrayList<T>();

        final Enumeration<URL> e = classLoader.getResources("META-INF/services/"+type.getName());
        while (e.hasMoreElements()) {
            URL url = e.nextElement();
            BufferedReader configFile = new BufferedReader(new InputStreamReader(url.openStream(),"UTF-8"));
            String line;
            while ((line = configFile.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#") || line.length()==0)   continue;

                try {
                    Class<?> t = classLoader.loadClass(line);
                    if (!type.isAssignableFrom(t))   continue;      // invalid type
                    
                    result.add(type.cast(t.newInstance()));
                } catch (ClassNotFoundException x) {
                    LOGGER.log(WARNING,"Failed to load "+line,x);
                } catch (InstantiationException x) {
                    LOGGER.log(WARNING,"Failed to load "+line,x);
                } catch (IllegalAccessException x) {
                    LOGGER.log(WARNING,"Failed to load "+line,x);
                }
            }
        }

        return result;
    }

    private static final Logger LOGGER = Logger.getLogger(ServiceLoader.class.getName());
}
