package jenkins.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import jenkins.ClassLoaderReflectionToolkit;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Path;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * {@link org.apache.tools.ant.AntClassLoader} with loosened visibility for use with {@link
 * ClassLoaderReflectionToolkit}.
 */
@Restricted(NoExternalUse.class)
public class AntClassLoader extends org.apache.tools.ant.AntClassLoader implements JenkinsClassLoader {

    public AntClassLoader(final ClassLoader parent, final Project project, final Path classpath) {
        super(parent, project, classpath);
    }

    public AntClassLoader() {}

    public AntClassLoader(final Project project, final Path classpath) {
        super(project, classpath);
    }

    public AntClassLoader(final ClassLoader parent, final Project project,
        final Path classpath, final boolean parentFirst) {
        super(parent, project, classpath, parentFirst);
    }

    public AntClassLoader(final Project project, final Path classpath,
        final boolean parentFirst) {
        super(project, classpath, parentFirst);
    }

    public AntClassLoader(final ClassLoader parent, final boolean parentFirst) {
        super(parent, parentFirst);
    }

    public void addPathFiles(Collection<File> paths) throws IOException {
        for (File f : paths) {
            addPathFile(f);
        }
    }

    @Override
    public URL findResource(final String name) {
        return super.findResource(name);
    }

    @Override
    public Enumeration<URL> findResources(final String name) throws IOException {
        return super.findResources(name);
    }

    @Override
    public Class<?> findLoadedClass2(String name) {
        return super.findLoadedClass(name);
    }

    @Override
    public Object getClassLoadingLock(String className) {
        return super.getClassLoadingLock(className);
    }
}
