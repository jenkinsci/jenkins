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

import java.io.InputStream;
import java.io.ByteArrayOutputStream;

/**
 * {@link Callable} used to verify the classloader used.
 *
 * @author Kohsuke Kawaguchi
 */
public class TestCallable implements Callable {
    public Object call() throws Throwable {
        Object[] r = new Object[4];

        // to verify that this class is indeed loaded by the remote classloader
        r[0] = getClass().getClassLoader().toString();

        // to make sure that we can also load resources
        String resName = "TestCallable.class";
        InputStream in = getClass().getResourceAsStream(resName);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        byte[] buf = new byte[8192];
        int len;
        while((len=in.read(buf))>0)
            baos.write(buf,0,len);
        in.close();

        r[1] = baos.toByteArray();

        // to make sure multiple resource look ups are cached.
        r[2] = getClass().getResource(resName);
        r[3] = getClass().getResource(resName);

        return r;
    }

}
