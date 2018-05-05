/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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

import org.junit.Test;
import static org.junit.Assert.*;

import hudson.Util;

/**
 * Test for {@link Util.isOverridden} method.
 */
public class IsOverriddenTest {

    /**
     * Test that a method is found by isOverridden even when it is inherited from an intermediate class.
     */
    @Test
    public void isOverriddenTest() {
        assertTrue(Util.isOverridden(Base.class, Derived.class, "method"));
        assertTrue(Util.isOverridden(Base.class, Intermediate.class, "method"));
        assertFalse(Util.isOverridden(Base.class, Base.class, "method"));
        assertTrue(Util.isOverridden(Base.class, Intermediate.class, "setX", Object.class));
        assertTrue(Util.isOverridden(Base.class, Intermediate.class, "getX"));
    }

    /**
     * Negative test.
     * Trying to check for a method which does not exist in the hierarchy,
     */
    @Test(expected = IllegalArgumentException.class)
    public void isOverriddenNegativeTest() {
        Util.isOverridden(Base.class, Derived.class, "method2");
    }

    /**
     * Do not inspect private methods.
     */
    @Test(expected = IllegalArgumentException.class)
    public void avoidPrivateMethodsInspection() {
        Util.isOverridden(Base.class, Intermediate.class, "aPrivateMethod");
    }

    public abstract class Base<T> {
        protected abstract void method();
        private void aPrivateMethod() {}
        public void setX(T t) {}
        public T getX() { return null; }
    }
    public abstract class Intermediate extends Base<Integer> {
        protected void method() {}
        private void aPrivateMethod() {}
        public void setX(Integer i) {}
        public Integer getX() { return 0; }
    }
    public class Derived extends Intermediate {}

}

