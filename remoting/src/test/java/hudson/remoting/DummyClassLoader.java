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
package hudson.remoting;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.net.URL;

/**
 * Used to load a dummy class <tt>hudson.remoting.test.TestCallable</tt>
 * out of nowhere, to test {@link RemoteClassLoader}.
 *
 * @author Kohsuke Kawaguchi
 */
class DummyClassLoader extends ClassLoader {
    public DummyClassLoader(ClassLoader parent) {
        super(parent);
    }


    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if(name.equals("hudson.remoting.test.TestCallable")) {
            // rename a class
            try {
                byte[] bytes = loadTransformedClassImage(name);
                return defineClass(name,bytes,0,bytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException("Bytecode manipulation failed",e);
            }
        }

        return super.findClass(name);
    }

    private byte[] loadTransformedClassImage(final String name) throws IOException {
        InputStream in = getResourceAsStream("hudson/remoting/TestCallable.class");

        // rename a class
        ClassReader cr = new ClassReader(in);
        ClassWriter w = new ClassWriter(cr,true) {
            public void visit(int version, int access, String _name, String sig, String superName, String[] interfaces) {
                super.visit(version, access, name.replace('.','/'), sig, superName, interfaces);
            }
        };
        cr.accept(w,false);

        return w.toByteArray();
    }


    protected URL findResource(String name) {
        if(name.equals("hudson/remoting/test/TestCallable.class")) {
            try {
                File f = File.createTempFile("rmiTest","class");
                OutputStream os = new FileOutputStream(f);
                os.write(loadTransformedClassImage("hudson.remoting.test.TestCallable"));
                os.close();
                f.deleteOnExit();
                return f.toURL();
            } catch (IOException e) {
                return null;
            }
        }
        return super.findResource(name);
    }
}
