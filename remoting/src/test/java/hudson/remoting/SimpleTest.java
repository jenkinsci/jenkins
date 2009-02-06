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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Testing the basic features.
 * 
 * @author Kohsuke Kawaguchi
 */
public class SimpleTest extends RmiTestBase {
    public void test1() throws Exception {
        int r = channel.call(new Callable1());
        System.out.println("result=" + r);
        assertEquals(5,r);
    }

    public void test1Async() throws Exception {
        Future<Integer> r = channel.callAsync(new Callable1());
        System.out.println("result="+r.get());
        assertEquals(5,(int)r.get());
    }

    private static class Callable1 implements Callable<Integer, RuntimeException> {
        public Integer call() throws RuntimeException {
            System.err.println("invoked");
            return 5;
        }
    }


    public void test2() throws Exception {
        try {
            channel.call(new Callable2());
            fail();
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(),"foo");
        }
    }

    public void test2Async() throws Exception {
        try {
            Future<Integer> r = channel.callAsync(new Callable2());
            r.get();
            fail();
        } catch (ExecutionException e) {
            assertEquals(e.getCause().getMessage(),"foo");
        }
    }

    private static class Callable2 implements Callable<Integer, RuntimeException> {
        public Integer call() throws RuntimeException {
            throw new RuntimeException("foo");
        }
    }

    /**
     * Makes sure that proxied object can be sent back to the origin and resolve correctly.
     */
    public void test3() throws Exception {
        Foo c = new Foo() {};

        Foo r = channel.call(new Echo<Foo>(channel.export(Foo.class,c)));
        assertSame(c,r);
    }

    public static interface Foo {}

    private static class Echo<T> implements Callable<T,RuntimeException> {
        private final T t;

        Echo(T t) {
            this.t = t;
        }

        public T call() throws RuntimeException {
            return t;
        }
    }

    public static Test suite() throws Exception {
        return buildSuite(SimpleTest.class);
    }
}
