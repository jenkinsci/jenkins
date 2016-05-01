/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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

import hudson.Extension;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

import javax.servlet.http.HttpSessionEvent;
import java.io.IOException;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class JenkinsHttpSessionListenerTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    
    @Test
    public void testJenkinsHttpSessionListener() throws IOException, SAXException {
        Assert.assertFalse(TestHttpSessionListener.createCalled);
        
        JenkinsRule.WebClient webClient = jenkinsRule.createWebClient();
        webClient.goTo("");

        Assert.assertTrue(TestHttpSessionListener.createCalled);
    }
    
    @Extension
    public static final class TestHttpSessionListener extends HttpSessionListener {
        private static boolean createCalled = false;
        @Override
        public void sessionCreated(HttpSessionEvent httpSessionEvent) {
            createCalled = true;
        }
    } 
}
