package jenkins.util;

import org.apache.tools.ant.Project;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Vector;

/**
 * As of 1.8.0, {@link org.apache.tools.ant.AntClassLoader} doesn't implement {@link #findResource(String)}
 * in any meaningful way, which breaks fast lookup. Implement it properly.
 */
public class AntWithFindResourceClassLoader extends AntClassLoader implements Closeable {
    private final Vector pathComponents;

    public AntWithFindResourceClassLoader(ClassLoader parent, boolean parentFirst) {
        super(parent, parentFirst);

        try {
            Field $pathComponents = AntClassLoader.class.getDeclaredField("pathComponents");
            $pathComponents.setAccessible(true);
            pathComponents = (Vector)$pathComponents.get(this);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new Error(e);
        }
    }

    public void addPathFiles(Collection<File> paths) throws IOException {
        for (File f : paths)
            addPathFile(f);
    }

    public void close() throws IOException {
        cleanup();
    }

    @Override
    protected URL findResource(String name) {
        URL url = null;

        // try and load from this loader if the parent either didn't find
        // it or wasn't consulted.
        Enumeration e = pathComponents.elements();
        while (e.hasMoreElements() && url == null) {
            File pathComponent = (File) e.nextElement();
            url = getResourceURL(pathComponent, name);
            if (url != null) {
                log("Resource " + name + " loaded from ant loader", Project.MSG_DEBUG);
            }
        }

        return url;
    }

}
