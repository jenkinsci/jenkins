/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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
package jenkins.model;

import org.junit.Test;

import org.junit.Assert;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class JenkinsTest {
    
    @Test
    public void test_isPluginJenkinsModuleRes() {
        Assert.assertTrue(Jenkins.isPluginJenkinsModuleRes("/plugin/abc/jsmodules/script.js"));
        Assert.assertTrue(Jenkins.isPluginJenkinsModuleRes("/plugin/abc/jsmodules/style.css"));
        Assert.assertTrue(Jenkins.isPluginJenkinsModuleRes("/plugin/abc/jsmodules/resources/style.css"));

        // Not a .js or .css file
        Assert.assertFalse(Jenkins.isPluginJenkinsModuleRes("/plugin/abc/jsmodules/blah.txt"));
        
        // Not in the "jsmodules" dir
        Assert.assertFalse(Jenkins.isPluginJenkinsModuleRes("/plugin/abc/style.css"));

        // Not in a plugin dir
        Assert.assertFalse(Jenkins.isPluginJenkinsModuleRes("/plugin/jsmodules/style.css"));

        // The "plugin" dir is not the root dir
        Assert.assertFalse(Jenkins.isPluginJenkinsModuleRes("/abc/jsmodules/style.css"));
    }
    
}
