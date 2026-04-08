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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.Util;
import java.io.PrintWriter;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

/**
 * Test for {@link Util#isOverridden} method.
 */
class IsOverriddenTest {

    /**
     * Test that a method is found by isOverridden even when it is inherited from an intermediate class.
     */
    @Test
    void isOverriddenTest() {
        assertTrue(Util.isOverridden(Base.class, Derived.class, "method"));
        assertTrue(Util.isOverridden(Base.class, Intermediate.class, "method"));
        assertFalse(Util.isOverridden(Base.class, Base.class, "method"));
        assertFalse(Util.isOverridden(Throwable.class, Throwable.class, "printStackTrace", PrintWriter.class));
        assertFalse(Util.isOverridden(Throwable.class, Exception.class, "printStackTrace", PrintWriter.class));
        assertTrue(Util.isOverridden(Base.class, Intermediate.class, "setX", Object.class));
        assertTrue(Util.isOverridden(Base.class, Intermediate.class, "getX"));
    }

    /**
     * Negative test.
     * Trying to check for a method which does not exist in the hierarchy.
     */
    @Test
    void isOverriddenNegativeTest() {
        assertThrows(IllegalArgumentException.class, () -> Util.isOverridden(Base.class, Derived.class, "method2"));
    }

    /** Specifying a base class that is not a base class should result in an error. */
    @Test
    void badHierarchyIsReported() {
        assertThrows(IllegalArgumentException.class, () -> Util.isOverridden(Derived.class, Base.class, "method"));
    }

    /**
     * Do not inspect private methods.
     */
    @Test
    void avoidPrivateMethodsInspection() {
        assertThrows(IllegalArgumentException.class, () -> Util.isOverridden(Base.class, Intermediate.class, "aPrivateMethod"));
    }

    public abstract static class Base<T> {
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

    @Issue("JENKINS-62723")
    @Test
    void finalOverrides() {
        assertAll(
                () -> assertThat("X1 overrides X.m1", Util.isOverridden(X.class, X1.class, "m1"), is(true)),
                () -> assertThat("x1 does not override x.m2", Util.isOverridden(X.class, X1.class, "m2"), is(false)),
                () -> assertThat("X2 overrides X.m1", Util.isOverridden(X.class, X2.class, "m1"), is(true)),
                () -> assertThat("X2 does not override X.m2", Util.isOverridden(X.class, X2.class, "m2"), is(false)),
                () -> assertThat("X3 overrides X.m1", Util.isOverridden(X.class, X3.class, "m1"), is(true)),
                () -> assertThat("X3 overrides X.m2", Util.isOverridden(X.class, X3.class, "m2"), is(true)),
                () -> assertThat("X4 overrides X.m1", Util.isOverridden(X.class, X4.class, "m1"), is(true)),
                () -> assertThat("X4 overrides X.m2", Util.isOverridden(X.class, X4.class, "m2"), is(true))
        );
    }

    public interface X {
        void m1();

        default void m2() {}
    }

    public static class X1 implements X {
        public void m1() {}
    }

    public static class X2 implements X {
        public final void m1() {}
    }

    public static class X3 implements X {
        public void m1() {}

        @Override
        public void m2() {}
    }

    public static class X4 implements X {
        public void m1() {}

        @Override
        public final void m2() {}
    }

    @Issue("JENKINS-62723")
    @Test
    void baseInterface() {
        // Normal case: classes implementing interface methods
        assertAll(
                () -> assertThat("I1 does not override I1.foo", Util.isOverridden(I1.class, I1.class, "foo"), is(false)),
                () -> assertThat("I2 does not override I1.foo", Util.isOverridden(I1.class, I2.class, "foo"), is(false)),
                () -> assertThat("C1 does not override I1.foo", Util.isOverridden(I1.class, C1.class, "foo"), is(false)),
                () -> assertThat("C2 does not override I1.foo", Util.isOverridden(I1.class, C2.class, "foo"), is(false)),
                () -> assertThat("C3 overrides I1.foo", Util.isOverridden(I1.class, C3.class, "foo"), is(true)),
                () -> assertThat("C4 overrides I1.foo", Util.isOverridden(I1.class, C4.class, "foo"), is(true)),
                // Special case: interfaces providing default implementation of base interface
                () -> assertThat("I1 does not override I1.bar", Util.isOverridden(I1.class, I1.class, "bar"), is(false)),
                () -> assertThat("I2 overrides I1.bar", Util.isOverridden(I1.class, I2.class, "bar"), is(true)),
                () -> assertThat("C1 does not override I1.bar", Util.isOverridden(I1.class, C1.class, "bar"), is(false)),
                () -> assertThat("C2 overrides I1.bar (via I2)", Util.isOverridden(I1.class, C2.class, "bar"), is(true)),
                () -> assertThat("C3 overrides I1.bar (via I2)", Util.isOverridden(I1.class, C3.class, "bar"), is(true)),
                () -> assertThat("C4 overrides I1.bar", Util.isOverridden(I1.class, C4.class, "bar"), is(true))
        );
    }

    private interface I1 {
        String foo();

        String bar();
    }

    private interface I2 extends I1 {
        default String bar() { return "default"; }
    }

    private abstract static class C1 implements I1 {
    }

    private abstract static class C2 extends C1 implements I2 {
        @Override public abstract String foo();
    }

    private abstract static class C3 extends C2 {
        @Override public String foo() { return "foo"; }
    }

    private static class C4 extends C3 implements I1 {
        @Override public String bar() { return "bar"; }
    }


}
