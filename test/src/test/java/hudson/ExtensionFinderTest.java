/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, CloudBees, Inc.
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
package hudson;

import com.google.inject.AbstractModule;
import hudson.model.PageDecorator;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestEnvironment;
import org.jvnet.hudson.test.TestExtension;

import javax.inject.Inject;
import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExtensionFinderTest extends HudsonTestCase {
    /**
     * It's OK for some extensions to fail to load. The system needs to tolerate that.
     */
    public void testFailingInstance() {
        FailingExtension i = PageDecorator.all().get(FailingExtension.class);
        assertNull("Instantiation should have failed",i);
        assertTrue("Instantiation should have been attempted", FailingExtension.error);
    }

    @TestExtension("testFailingInstance")
    public static class FailingExtension extends PageDecorator {
        public FailingExtension() {
            super(FailingExtension.class);
            error = true;
            throw new LinkageError();   // this component fails to load
        }
        public static boolean error;
    }





    /**
     * Extensions are Guice components, so it should support injection.
     */
    public void testInjection() {
        InjectingExtension i = PageDecorator.all().get(InjectingExtension.class);
        assertNotNull(i.foo);
        assertEquals("lion king",i.value);
    }

    @TestExtension("testInjection")
    public static class InjectingExtension extends PageDecorator {
        @Inject
        Foo foo;

        @Inject @LionKing
        String value;


        public InjectingExtension() {
            super(InjectingExtension.class);
        }
        public static class Foo {}
    }


    @Retention(RetentionPolicy.RUNTIME) @Qualifier
    public @interface LionKing {}

    @Extension
    public static class ModuleImpl extends AbstractModule {
        protected void configure() {
            if (TestEnvironment.get().testCase instanceof ExtensionFinderTest) {
                bind(String.class).annotatedWith(LionKing.class).toInstance("lion king");
            }
        }
    }


    /**
     * Tests the error recovery behaviour.
     *
     * One failure in binding definition shouldn't prevent Jenkins from booting.
     */
    public void testErrorRecovery() {
        BrokenExtension i = PageDecorator.all().get(BrokenExtension.class);
        assertNull(i);
    }

    @TestExtension("testErrorRecovery")
    public static class BrokenExtension extends PageDecorator {
        public BrokenExtension() {
            super(InjectingExtension.class);
            
            throw new Error();
        }
    }
}
