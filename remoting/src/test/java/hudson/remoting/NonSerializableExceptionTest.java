/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

import java.net.SocketException;

/**
 * @author Kohsuke Kawaguchi
 */
public class NonSerializableExceptionTest extends RmiTestBase {
    /**
     * Makes sure non-serializable exceptions are gracefully handled.
     *
     * HUDSON-1041.
     */
    public void test1() throws Throwable {
        try {
            channel.call(new Failure());
        } catch (ProxyException p) {
            // verify that we got the right kind of exception
            assertTrue(p.getMessage().contains("NoneSerializableException"));
            assertTrue(p.getMessage().contains("message1"));
            ProxyException nested = p.getCause();
            assertTrue(nested.getMessage().contains("SocketException"));
            assertTrue(nested.getMessage().contains("message2"));
            assertNull(nested.getCause());
        }
    }

    private static final class NoneSerializableException extends Exception {
        private final Object o = new Object(); // this is not serializable

        private NoneSerializableException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    private static final class Failure implements Callable {
        public Object call() throws Throwable {
            throw new NoneSerializableException("message1",new SocketException("message2"));
        }
    }
}
