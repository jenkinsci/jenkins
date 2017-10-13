/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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

import org.eclipse.jetty.server.handler.ContextHandler;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import javax.servlet.ServletContextEvent;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Tests of {@link ServletContextSystemPropertiesProvider}
 * @author Oleg Nenashev
 */
public class ServletContextSystemPropertiesProviderTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() {
        // Does not matter what we call, the fields are static
        new ServletContextSystemPropertiesProvider().contextInitialized(new ServletContextEvent(j.jenkins.servletContext));
    }

    @After
    public void tearDown() {
        new ServletContextSystemPropertiesProvider().contextDestroyed(new ServletContextEvent(j.jenkins.servletContext));
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
    public void shouldUseSystemPropertyAsAHigherPriority() throws Exception {
        setWebAppInitParameter("install-wizard-path", "myVal1");
        System.setProperty("install-wizard-path", "myVal2");
        assertThat("System property should take system property with a higher priority",
                SystemProperties.getString("install-wizard-path"), equalTo("myVal2"));
    }

    /**
     * Hack to set a web app initial parameter afterwards. Only works with Jetty.
     * @param property property to set
     * @param value value of the property
     */
    protected void setWebAppInitParameter(String property, String value) {
        Assume.assumeThat(j.jenkins.servletContext, Matchers.instanceOf(ContextHandler.Context.class));
        ((ContextHandler.Context)j.jenkins.servletContext).getContextHandler().getInitParams().put(property, value);
    }
}
