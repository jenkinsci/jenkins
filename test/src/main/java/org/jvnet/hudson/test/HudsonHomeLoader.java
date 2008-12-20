package org.jvnet.hudson.test;

import hudson.FilePath;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Controls how a {@link HudsonTestCase} initializes <tt>HUDSON_HOME</tt>.
 *
 * @author Kohsuke Kawaguchi
 */
public interface HudsonHomeLoader {
    /** 
     * Returns a directory to be used as <tt>HUDSON_HOME</tt>
     *
     * @throws Exception
     *      to cause a test to fail.
     */
    File allocate() throws Exception;

    /**
     * Allocates a new empty directory, meaning this will emulate the fresh Hudson installation.
     */
    public static final HudsonHomeLoader NEW = new HudsonHomeLoader() {
        public File allocate() throws IOException {
            return TestEnvironment.get().temporaryDirectoryAllocator.allocate();
        }
    };

    /**
     * Allocates a new directory by copying from an existing directory, or unzipping from a zip file.
     */
    public static final class CopyExisting implements HudsonHomeLoader {
        private final URL source;

        /**
         * Either a zip file or a directory that contains the home image.
         */
        public CopyExisting(File source) throws MalformedURLException {
            this(source.toURL());
        }

        /**
         * Extracts from a zip file in the resource.
         *
         * <p>
         * This is useful in case you want to have a test data in the resources.
         * Only file URL is supported. 
         */
        public CopyExisting(URL source) {
            this.source = source;
        }

        public File allocate() throws Exception {
            File target = NEW.allocate();
            if(source.getProtocol().equals("file")) {
                File src = new File(source.getPath());
                if(src.isDirectory())
                    new FilePath(src).copyRecursiveTo("**/*",new FilePath(target));
                else
                if(src.getName().endsWith(".zip"))
                    new FilePath(src).unzip(new FilePath(target));
            } else {
                File tmp = File.createTempFile("hudson","zip");
                try {
                    FileUtils.copyURLToFile(source,tmp);
                    new FilePath(tmp).unzip(new FilePath(target));
                } finally {
                    tmp.delete();
                }
            }
            return target;
        }
    }

    /**
     * Allocates a new directory by copying from a test resource
     */
    public static final class Local implements HudsonHomeLoader {
        private final Method testMethod;

        public Local(Method testMethod) {
            this.testMethod = testMethod;
        }

        public File allocate() throws Exception {
            URL res = findDataResource();
            if(!res.getProtocol().equals("file"))
                throw new AssertionError("Test data is not available in the file system: "+res);
            // if we picked up a directory, it's one level above config.xml
            File home = new File(res.getPath());
            if(!home.getName().endsWith(".zip"))
                home = home.getParentFile();

            return new CopyExisting(home).allocate();
        }

        private URL findDataResource() {
            // first, check method specific resource
            Class<?> clazz = testMethod.getDeclaringClass();
            
            for( String middle : new String[]{ '/'+testMethod.getName(), "" }) {
                for( String suffix : SUFFIXES ) {
                    URL res = clazz.getResource(clazz.getSimpleName() + middle+suffix);
                    if(res!=null)   return res;
                }
            }

            throw new AssertionError("No test resource was found for "+testMethod);
        }

        private static final String[] SUFFIXES = {"/config.xml",".zip"};
    }
}
