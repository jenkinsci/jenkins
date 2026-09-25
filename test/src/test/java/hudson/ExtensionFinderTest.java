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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.inject.AbstractModule;
import com.google.inject.ImplementedBy;
import hudson.model.PageDecorator;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestEnvironment;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class ExtensionFinderTest {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    /**
     * It's OK for some extensions to fail to load. The system needs to tolerate that.
     */
    @Test
    void failingInstance() {
        FailingExtension i = PageDecorator.all().get(FailingExtension.class);
        assertNull(i, "Instantiation should have failed");
        assertTrue(FailingExtension.error, "Instantiation should have been attempted");
    }

    @TestExtension("failingInstance")
    public static class FailingExtension extends PageDecorator {
        @SuppressWarnings("checkstyle:redundantmodifier")
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
    void injection() {
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

        @SuppressWarnings("checkstyle:redundantmodifier")
        public InjectingExtension() {
            super(InjectingExtension.class);
        }

        public static class Foo {}
    }

    /**
     * Extensions are Guice components, so it should support injection.
     */
    @Test
    void legacyInjection() {
        LegacyInjectingExtension i = PageDecorator.all().get(LegacyInjectingExtension.class);
        assertNotNull(i.foo);
        assertEquals("lion king", i.value);
    }

    @TestExtension("legacyInjection")
    public static class LegacyInjectingExtension extends PageDecorator {
        @javax.inject.Inject
        Foo foo;

        @javax.inject.Inject
        @LionKing
        String value;

        @SuppressWarnings("checkstyle:redundantmodifier")
        public LegacyInjectingExtension() {
            super(LegacyInjectingExtension.class);
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
                    && ("injection".equals(environment.description().getMethodName()) || "legacyInjection".equals(environment.description().getMethodName()))) {
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
    void errorRecovery() {
        BrokenExtension i = PageDecorator.all().get(BrokenExtension.class);
        assertNull(i);
    }

    @TestExtension("errorRecovery")
    public static class BrokenExtension extends PageDecorator {
        @SuppressWarnings("checkstyle:redundantmodifier")
        public BrokenExtension() {
            super(InjectingExtension.class);

            throw new Error();
        }
    }

    @Test
    void injectMutualRecursion() {
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
    void injectInterface() {
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
