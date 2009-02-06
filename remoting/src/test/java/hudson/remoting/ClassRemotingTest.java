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

import junit.framework.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.EmptyVisitor;

/**
 * Test class image forwarding.
 *
 * @author Kohsuke Kawaguchi
 */
public class ClassRemotingTest extends RmiTestBase {
    public void test1() throws Throwable {
        // call a class that's only available on DummyClassLoader, so that on the remote channel
        // it will be fetched from this class loader and not from the system classloader.
        DummyClassLoader cl = new DummyClassLoader(this.getClass().getClassLoader());
        Callable c = (Callable) cl.loadClass("hudson.remoting.test.TestCallable").newInstance();

        Object[] r = (Object[]) channel.call(c);

        System.out.println(r[0]);

        assertTrue(r[0].toString().startsWith("hudson.remoting.RemoteClassLoader@"));

        // make sure the bytes are what we are expecting
        System.out.println("Resource is "+((byte[])r[1]).length+" bytes");
        ClassReader cr = new ClassReader((byte[])r[1]);
        cr.accept(new EmptyVisitor(),false);

        // make sure cache is taking effect
        System.out.println(r[2]);
        System.out.println(r[3]);
        assertEquals(r[2],r[3]);
    }

    public static Test suite() throws Exception {
        return buildSuite(ClassRemotingTest.class);
    }
}
