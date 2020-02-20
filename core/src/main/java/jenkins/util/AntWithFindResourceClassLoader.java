package jenkins.util;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

/**
 * As of 1.8.0, {@link org.apache.tools.ant.AntClassLoader} doesn't implement {@link #findResource(String)}
 * in any meaningful way, which breaks fast lookup. Implement it properly.
 */
public class AntWithFindResourceClassLoader extends AntClassLoader implements Closeable {
    private final ArrayList<File> pathComponents;

    public AntWithFindResourceClassLoader(ClassLoader parent, boolean parentFirst) {
        super(parent, parentFirst);

        try {
            Field $pathComponents = AntClassLoader.class.getDeclaredField("pathComponents");
            $pathComponents.setAccessible(true);
            pathComponents = (ArrayList<File>)$pathComponents.get(this);
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
        // try and load from this loader if the parent either didn't find
        // it or wasn't consulted.
        return getUrl(pathComponents, name);
    }

}
