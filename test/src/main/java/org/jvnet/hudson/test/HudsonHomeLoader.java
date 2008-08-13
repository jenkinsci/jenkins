package org.jvnet.hudson.test;

import hudson.FilePath;

import java.io.File;
import java.io.IOException;
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
    public static class CopyExisting implements HudsonHomeLoader {
        private final File source;

        /**
         * Either a zip file or a directory that contains the home image.
         */
        public CopyExisting(File source) {
            if(source==null)    throw new IllegalArgumentException();
            this.source = source;
        }

        /**
         * Extracts from a zip file in the resource.
         *
         * <p>
         * This is useful in case you want to have a test data in the resources.
         * Only file URL is supported. 
         */
        public CopyExisting(URL source) {
            if(!source.getProtocol().equals("file"))
                throw new UnsupportedOperationException("Unsupported protocol: "+source);
            this.source = new File(source.getPath());
        }

        public File allocate() throws Exception {
            File target = NEW.allocate();
            if(source.isDirectory())
                new FilePath(source).copyRecursiveTo("**/*",new FilePath(target));
            else
            if(source.getName().endsWith(".zip"))
                new FilePath(source).unzip(new FilePath(target));
            return target;
        }
    }
}
