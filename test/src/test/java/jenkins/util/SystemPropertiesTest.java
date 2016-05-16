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

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import net.sourceforge.htmlunit.corejs.javascript.RhinoSecurityManager;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
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
        new SystemProperties().contextInitialized(new ServletContextEvent(j.jenkins.servletContext));
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
    public void shouldInitializeFromContext() throws Exception {
        ServletContext c = j.jenkins.servletContext;
        assertThat("System property should assign the value", 
                SystemProperties.getString("install-wizard-path"), equalTo("jsbundles/pluginSetupWizard"));
    }
    
    @Test
    public void shouldUseSystemPropertyAsAHighPriority() throws Exception {
        ServletContext c = j.jenkins.servletContext;
        System.setProperty("install-wizard-path", "myVal2");
        assertThat("System property should take system property with a high priority", 
                SystemProperties.getString("install-wizard-path"), equalTo("myVal2"));
    }
}
