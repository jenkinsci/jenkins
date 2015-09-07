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

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Dedicated test for testing version persistence in the config.xml file.
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class JenkinsVersionSaveTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    // NOTICE: Don't add other tests here.
    // 
    // We want to make sure the version is persisted WITHOUT config changes e.g. on install.
    // Other tests might interfere with that, so safer to just leave this test isolated.
    
    @Test
    public void saveVersion() throws IOException {
        File rootDir = j.jenkins.getRootDir();
        File configFile = new File(rootDir, "config.xml");
        String configXML = FileUtils.readFileToString(configFile);
        
        assertFalse("Jenkins version not initialized.", Jenkins.VERSION.equals("?"));
        assertTrue("Jenkins version not as expected. Expected " + Jenkins.VERSION, configXML.contains(Jenkins.VERSION));
    }
}
