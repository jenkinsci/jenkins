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

package jenkins.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import jakarta.servlet.ServletContextEvent;
import org.eclipse.jetty.ee9.webapp.WebAppContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests of {@link SystemProperties}.
 * @author Oleg Nenashev
 */
@WithJenkins
class SystemPropertiesTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        new SystemProperties.Listener().contextInitialized(new ServletContextEvent(j.jenkins.getServletContext()));
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("foo.bar");
    }

    @Test
    void shouldReturnNullIfUndefined() {
        assertThat("Properties should be null by default",
                SystemProperties.getString("foo.bar"), nullValue());
    }

    @Test
    void shouldInitializeFromSystemProperty() {
        System.setProperty("foo.bar", "myVal");
        assertThat("System property should assign the value",
                SystemProperties.getString("foo.bar"), equalTo("myVal"));
    }

    @Test
    void shouldInitializeFromWebAppProperty() {
        assertThat("Property is undefined before test",
                SystemProperties.getString("foo.bar"), equalTo(null));
        setWebAppInitParameter("foo.bar", "myVal");
        assertThat("Web App property should assign the value",
                SystemProperties.getString("foo.bar"), equalTo("myVal"));
    }

    @Test
    void shouldUseSystemPropertyAsAHighPriority() {
        setWebAppInitParameter("install-wizard-path", "myVal1");
        System.setProperty("install-wizard-path", "myVal2");
        assertThat("System property should take system property with a high priority",
                SystemProperties.getString("install-wizard-path"), equalTo("myVal2"));
    }

    @Test
    void shouldReturnWebAppPropertyIfSystemPropertyNotSetAndDefaultIsSet() {
        assertThat("Property is undefined before test",
                SystemProperties.getString("foo.bar"), equalTo(null));
        setWebAppInitParameter("foo.bar", "myVal");
        assertThat("Should return web app property if system property is not set and default value is set",
                SystemProperties.getString("foo.bar", "defaultVal"), equalTo("myVal"));
    }

    /**
     * Hack to set a web app initial parameter afterwards. Only works with Jetty.
     * @param property property to set
     * @param value value of the property
     */
    protected void setWebAppInitParameter(String property, String value) {
        assumeTrue(j.jenkins.getServletContext() instanceof WebAppContext.Context);
        ((WebAppContext.Context) j.jenkins.getServletContext()).getContextHandler().getInitParams().put(property, value);
    }
}
