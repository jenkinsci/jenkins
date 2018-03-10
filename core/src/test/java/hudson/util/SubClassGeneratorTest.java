/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package hudson.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class SubClassGeneratorTest {

    public static class Foo {
        String s;
        double x;
        int y;
        public Foo() {}
        public Foo(String s) {this.s=s;}
        public Foo(double x, int y) {this.x=x;this.y=y;}
    }

    @Test
    public void foo() throws Exception {
        Class<? extends Foo> c = new SubClassGenerator(getClass().getClassLoader()).generate(Foo.class, "12345");
        assertEquals("12345",c.getName());

        c.newInstance();

        Foo f = c.getConstructor(String.class).newInstance("aaa");
        assertEquals("aaa",f.s);

        f = c.getConstructor(double.class,int.class).newInstance(1.0,3);
        assertEquals(1.0,f.x,0);
        assertEquals(3,f.y);
    }
}
