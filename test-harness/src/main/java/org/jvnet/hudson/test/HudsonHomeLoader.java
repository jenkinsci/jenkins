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
package org.jvnet.hudson.test;

import hudson.FilePath;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Controls how a {@link HudsonTestCase} initializes <tt>JENKINS_HOME</tt>.
 *
 * @author Kohsuke Kawaguchi
 */
public interface HudsonHomeLoader {
    /** 
     * Returns a directory to be used as <tt>JENKINS_HOME</tt>
     *
     * @throws Exception
     *      to cause a test to fail.
     */
    File allocate() throws Exception;

    /**
     * Allocates a new empty directory, meaning this will emulate the fresh Hudson installation.
     */
    HudsonHomeLoader NEW = new HudsonHomeLoader() {
        public File allocate() throws IOException {
            return TestEnvironment.get().temporaryDirectoryAllocator.allocate();
        }
    };

    /**
     * Allocates a new directory by copying from an existing directory, or unzipping from a zip file.
     */
    final class CopyExisting implements HudsonHomeLoader {
        private final URL source;

        /**
         * Either a zip file or a directory that contains the home image.
         */
        public CopyExisting(File source) throws MalformedURLException {
            this(source.toURI().toURL());
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
                File src = new File(source.toURI());
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
    final class Local implements HudsonHomeLoader {
        private final Method testMethod;

        public Local(Method testMethod) {
            this.testMethod = testMethod;
        }

        public File allocate() throws Exception {
            URL res = findDataResource();
            if(!res.getProtocol().equals("file"))
                throw new AssertionError("Test data is not available in the file system: "+res);
            // if we picked up a directory, it's one level above config.xml
            File home = new File(res.toURI());
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
