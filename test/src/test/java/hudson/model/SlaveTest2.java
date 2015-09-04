/*
 * The MIT License
 *
 * Copyright (c) 2015 CloudBees, Inc.
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

import java.net.MalformedURLException;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;
import org.jvnet.hudson.test.Bug;

/**
 * Tests for the {@link Slave} class.
 * There is also a Groovy implementation of such test file, hence the class name 
 * has an index.
 * @author Oleg Nenashev
 */
public class SlaveTest2 {
    
    @Rule
    public JenkinsRule rule = new JenkinsRule();
    
    @Test
    //TODO: uncomment after upgrading to the new core version (1.580+)
    //@Issue("SECURITY-195")
    public void shouldNotEscapeJnlpSlavesResources() throws Exception {
        Slave slave = rule.createSlave();
        
        // Raw access to API
        Slave.JnlpJar jnlpJar = slave.getComputer().getJnlpJars("../");
        try {
            jnlpJar.getURL();
        } catch (MalformedURLException ex) {
            // we expect the exception here
            ex.printStackTrace();
            return;
        }
        fail("Expected the MalformedURLException");
        
        // Access from a Web client
        JenkinsRule.WebClient client = rule.createWebClient();
        client.assertFails("jnlpJars/..%f", 500);
    }
}
