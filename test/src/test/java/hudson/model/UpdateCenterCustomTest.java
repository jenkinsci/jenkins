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


import javax.servlet.ServletContext;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;


/**
 * Tests of the custom {@link UpdateCenter} implementation.
 */
public class UpdateCenterCustomTest {
    
    @Rule
    public final JenkinsRule j = new CustomUpdateCenterRule(CustomUpdateCenter.class);
    
    @Test
    public void shouldStartupWithCustomUpdateCenter() throws Exception {
        UpdateCenter uc = j.jenkins.getUpdateCenter();
        assertThat("Update Center must be a custom instance", uc, instanceOf(CustomUpdateCenter.class));
    }

    // TODO: move to Jenkins Test Harness
    private static final class CustomUpdateCenterRule extends JenkinsRule {
        private final String updateCenterClassName;
        private String _oldValue = null;
        
        private static final String PROPERTY_NAME = UpdateCenter.class.getName()+".className";

        public CustomUpdateCenterRule(Class<?> ucClass) {
            this.updateCenterClassName = ucClass.getName();
        }

        @Override
        protected ServletContext createWebServer() throws Exception {
            _oldValue = System.getProperty(PROPERTY_NAME);
            System.setProperty(PROPERTY_NAME, updateCenterClassName);
            return super.createWebServer();
        }

        @Override
        public void after() throws Exception {
            if (_oldValue != null) {
                System.setProperty(PROPERTY_NAME, _oldValue);
            }
        }

        public String getUpdateCenterClassName() {
            return updateCenterClassName;
        }
    };
    
    public static final class CustomUpdateCenter extends UpdateCenter {

        public CustomUpdateCenter() {
            super();
        }
        
        public CustomUpdateCenter(UpdateCenterConfiguration config) {
            super(config);
        }
        
    }
}