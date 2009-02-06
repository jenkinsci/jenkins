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
import org.objectweb.asm.attrs.StackMapAttribute;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class PrefetchTest extends RmiTestBase {
    public void testPrefetch() throws Exception {
        VerifyTask vt = new VerifyTask();
        assertTrue( channel.preloadJar(vt,ClassReader.class));
        assertFalse(channel.preloadJar(vt,ClassReader.class));
        // TODO: how can I do a meaningful test of this feature?
        System.out.println(channel.call(vt));
    }

    private static class VerifyTask implements Callable<String,IOException> {
        public String call() throws IOException {
            StackMapAttribute sma = new StackMapAttribute();
            return Which.jarFile(sma.getClass()).getPath();
        }
    }
}
