/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package jenkins.security.stapler;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@Issue("SECURITY-400")
@For({StaplerDispatchable.class, StaplerNotDispatchable.class, TypedFilter.class})
@WithJenkins
class StaplerRoutableGetterTest extends StaplerAbstractTest {
    @TestExtension
    public static class TestRootAction extends AbstractUnprotectedRootAction {
        @Override
        public String getUrlName() {
            return "test";
        }

        public Object getFalseWithoutAnnotation() {
            return new Renderable();
        }

        @StaplerDispatchable
        public Object getFalseWithAnnotation() {
            return new Renderable();
        }

        public Renderable getTrueWithoutAnnotation() {
            return new Renderable();
        }

        @StaplerNotDispatchable
        public Renderable getTrueWithAnnotation() {
            return new Renderable();
        }

        @StaplerDispatchable
        @StaplerNotDispatchable
        public Renderable getPriorityToNegative() {
            return new Renderable();
        }
    }

    @Test
    void testForceGetterMethod() throws Exception {
        assertNotReachable("test/falseWithoutAnnotation/");
        assertNotReachable("test/falseWithoutAnnotation/valid/");

        filteredGetMethodTriggered = false;

        assertReachable("test/falseWithAnnotation/");
        assertReachable("test/falseWithAnnotation/valid/");
    }

    @Test
    void testForceNotGetterMethod() throws Exception {
        assertReachable("test/trueWithoutAnnotation/");
        assertReachable("test/trueWithoutAnnotation/valid/");
        assertNotReachable("test/trueWithAnnotation/");
        assertNotReachable("test/trueWithAnnotation/valid/");
    }

    @Test
    void testPriorityIsNegative() {
        assertNotReachable("test/priorityToNegative/");
    }

    public static class TestRootActionParent extends AbstractUnprotectedRootAction {
        @StaplerNotDispatchable
        public Renderable getParentKoButChildOk() {
            return new Renderable();
        }

        @StaplerNotDispatchable
        public Renderable getParentKoButChildNone() {
            return new Renderable();
        }

        public Renderable getParentNoneButChildOk() {
            return new Renderable();
        }

        public Renderable getParentNoneButChildKo() {
            return new Renderable();
        }

        @StaplerDispatchable
        public Renderable getParentOkButChildKo() {
            return new Renderable();
        }

        @StaplerDispatchable
        public Renderable getParentOkButChildNone() {
            return new Renderable();
        }
    }

    @TestExtension
    public static class TestRootActionChild extends TestRootActionParent {
        @Override
        public String getUrlName() {
            return "test-child";
        }

        @Override
        @StaplerDispatchable
        public Renderable getParentKoButChildOk() {
            return new Renderable();
        }

        @Override
        public Renderable getParentKoButChildNone() {
            return new Renderable();
        }

        @Override
        @StaplerDispatchable
        public Renderable getParentNoneButChildOk() {
            return new Renderable();
        }

        @Override
        @StaplerNotDispatchable
        public Renderable getParentNoneButChildKo() {
            return new Renderable();
        }

        @Override
        @StaplerNotDispatchable
        public Renderable getParentOkButChildKo() {
            return new Renderable();
        }

        @Override
        public Renderable getParentOkButChildNone() {
            return new Renderable();
        }
    }

    @Test
    void testInheritanceOfAnnotation_childHasLastWord() throws Exception {
        assertNotReachable("test-child/parentKoButChildOk/");
        assertNotReachable("test-child/parentKoButChildNone/");

        filteredGetMethodTriggered = false;

        assertReachable("test-child/parentNoneButChildOk/");

        assertNotReachable("test-child/parentNoneButChildKo/");
        assertNotReachable("test-child/parentOkButChildKo/");

        filteredGetMethodTriggered = false;

        assertReachable("test-child/parentOkButChildNone/");
    }
}
