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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.inject.AbstractModule;
import com.google.inject.ImplementedBy;
import hudson.model.PageDecorator;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestEnvironment;
import org.jvnet.hudson.test.TestExtension;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExtensionFinderTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    /**
     * It's OK for some extensions to fail to load. The system needs to tolerate that.
     */
    @Test
    public void failingInstance() {
        FailingExtension i = PageDecorator.all().get(FailingExtension.class);
        assertNull("Instantiation should have failed", i);
        assertTrue("Instantiation should have been attempted", FailingExtension.error);
    }

    @TestExtension("failingInstance")
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
    @Test
    public void injection() {
        InjectingExtension i = PageDecorator.all().get(InjectingExtension.class);
        assertNotNull(i.foo);
        assertEquals("lion king", i.value);
    }

    @TestExtension("injection")
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
        @Override
        protected void configure() {
            TestEnvironment environment = TestEnvironment.get();
            // JMH benchmarks do not initialize TestEnvironment, so check for null
            if (environment != null
                    && ExtensionFinderTest.class.getName().equals(environment.description().getClassName())
                    && "injection".equals(environment.description().getMethodName())) {
                bind(String.class).annotatedWith(LionKing.class).toInstance("lion king");
            }
        }
    }


    /**
     * Tests the error recovery behaviour.
     *
     * One failure in binding definition shouldn't prevent Jenkins from booting.
     */
    @Test
    public void errorRecovery() {
        BrokenExtension i = PageDecorator.all().get(BrokenExtension.class);
        assertNull(i);
    }

    @TestExtension("errorRecovery")
    public static class BrokenExtension extends PageDecorator {
        public BrokenExtension() {
            super(InjectingExtension.class);

            throw new Error();
        }
    }

    @Test
    public void injectMutualRecursion() {
        A a = ExtensionList.lookupSingleton(A.class);
        B b = ExtensionList.lookupSingleton(B.class);
        assertEquals(b, a.b);
        assertEquals(a, b.a);
    }

    @TestExtension("injectMutualRecursion")
    public static final class A {
        @Inject B b;
    }

    @TestExtension("injectMutualRecursion")
    public static final class B {
        @Inject A a;
    }

    @Issue("JENKINS-60816")
    @Test
    public void injectInterface() {
        assertThat(ExtensionList.lookupSingleton(X.class).xface, instanceOf(Impl.class));
    }

    @TestExtension("injectInterface")
    public static final class X {
        @Inject
        XFace xface;
    }

    @ImplementedBy(Impl.class)
    public interface XFace {}

    public static final class Impl implements XFace {}

}
