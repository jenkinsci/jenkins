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

import jakarta.servlet.ServletContextEvent;
import org.eclipse.jetty.ee10.webapp.WebAppContext;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests of {@link SystemProperties}.
 * @author Oleg Nenashev
 */
public class SystemPropertiesTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() {
        new SystemProperties.Listener().contextInitialized(new ServletContextEvent(j.jenkins.getServletContext()));
    }

    @After
    public void tearDown() {
        System.clearProperty("foo.bar");
    }


    @Test
    public void shouldReturnNullIfUndefined() throws Exception {
        assertThat("Properties should be null by default",
                SystemProperties.getString("foo.bar"), nullValue());
    }

    @Test
    public void shouldInitializeFromSystemProperty() throws Exception {
        System.setProperty("foo.bar", "myVal");
        assertThat("System property should assign the value",
                SystemProperties.getString("foo.bar"), equalTo("myVal"));
    }

    @Test
    public void shouldInitializeFromWebAppProperty() throws Exception {
        assertThat("Property is undefined before test",
                SystemProperties.getString("foo.bar"), equalTo(null));
        setWebAppInitParameter("foo.bar", "myVal");
        assertThat("Web App property should assign the value",
                SystemProperties.getString("foo.bar"), equalTo("myVal"));
    }

    @Test
    public void shouldUseSystemPropertyAsAHighPriority() throws Exception {
        setWebAppInitParameter("install-wizard-path", "myVal1");
        System.setProperty("install-wizard-path", "myVal2");
        assertThat("System property should take system property with a high priority",
                SystemProperties.getString("install-wizard-path"), equalTo("myVal2"));
    }

    @Test
    public void shouldReturnWebAppPropertyIfSystemPropertyNotSetAndDefaultIsSet() throws Exception {
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
        Assume.assumeThat(j.jenkins.getServletContext(), Matchers.instanceOf(WebAppContext.ServletApiContext.class));
        ((WebAppContext.ServletApiContext) j.jenkins.getServletContext()).getContext().getServletContextHandler().getInitParams().put(property, value);
    }
}
