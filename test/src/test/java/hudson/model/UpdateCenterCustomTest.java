/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
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

package hudson.model;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

import jakarta.servlet.ServletContext;
import java.io.File;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

/**
 * Tests of the custom {@link UpdateCenter} implementation.
 */
class UpdateCenterCustomTest {

    @RegisterExtension
    private final JenkinsSessionExtension session = new CustomUpdateCenterExtension(CustomUpdateCenterExtension.CustomUpdateCenter.class);

    @Test
    void shouldStartupWithCustomUpdateCenter() throws Throwable {
        session.then(j -> {
            UpdateCenter uc = j.jenkins.getUpdateCenter();
            assertThat("Update Center must be a custom instance", uc, instanceOf(CustomUpdateCenterExtension.CustomUpdateCenter.class));
        });
    }

    // TODO: move to Jenkins Test Harness
    private static final class CustomUpdateCenterExtension extends JenkinsSessionExtension {

        private int port;
        private Description description;
        private final String updateCenterClassName;

        CustomUpdateCenterExtension(Class<?> ucClass) {
            this.updateCenterClassName = ucClass.getName();
        }

        @Override
        public void beforeEach(ExtensionContext context) {
            super.beforeEach(context);
            description = Description.createTestDescription(
                    context.getTestClass().map(Class::getName).orElse(null),
                    context.getTestMethod().map(Method::getName).orElse(null),
                    context.getTestMethod().map(Method::getAnnotations).orElse(null));
        }

        @Override
        public void then(Step s) throws Throwable {
            CustomJenkinsRule r = new CustomJenkinsRule(updateCenterClassName, getHome(), port);
            r.apply(
                    new Statement() {
                        @Override
                        public void evaluate() throws Throwable {
                            port = r.getPort();
                            s.run(r);
                        }
                    },
                    description
            ).evaluate();
        }

        private static final class CustomJenkinsRule extends JenkinsRule {

            private final String updateCenterClassName;
            private String _oldValue = null;

            private static final String PROPERTY_NAME = UpdateCenter.class.getName() + ".className";

            CustomJenkinsRule(final String updateCenterClassName, File home, int port) {
                this.updateCenterClassName = updateCenterClassName;
                with(() -> home);
                localPort = port;
            }

            int getPort() {
                return localPort;
            }

            @Override
            protected ServletContext createWebServer2() throws Exception {
                _oldValue = System.getProperty(PROPERTY_NAME);
                System.setProperty(PROPERTY_NAME, updateCenterClassName);
                return super.createWebServer2();
            }

            @Override
            public void after() {
                if (_oldValue != null) {
                    System.setProperty(PROPERTY_NAME, _oldValue);
                }
            }
        }

        public static final class CustomUpdateCenter extends UpdateCenter {

            @SuppressWarnings("checkstyle:redundantmodifier")
            public CustomUpdateCenter() {
            }

            @SuppressWarnings("checkstyle:redundantmodifier")
            public CustomUpdateCenter(UpdateCenterConfiguration config) {
                super(config);
            }

        }
    }
}
