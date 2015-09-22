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
package jenkins.util;

import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.Locale;
import java.util.MissingResourceException;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class ResourceBundleUtilTest {

    /**
     * Test resource bundle loading for a defined locale.
     */
    @Test
    public void test_known_locale() {
        JSONObject bundle = ResourceBundleUtil.getBundle("hudson.logging.Messages", Locale.GERMAN);
        Assert.assertEquals("Initialisiere Log-Rekorder", bundle.getString("LogRecorderManager.init"));
        bundle = ResourceBundleUtil.getBundle("hudson.logging.Messages", new Locale("pt"));
        Assert.assertEquals("Inicializando registros de log", bundle.getString("LogRecorderManager.init"));
        
        // Test caching - should get the same bundle instance back...
        Assert.assertTrue(ResourceBundleUtil.getBundle("hudson.logging.Messages", new Locale("pt")) == bundle);
    }

    /**
     * Test that we get the "default" bundle for an unknown locale.
     */
    @Test
    public void test_unknown_locale() {
        JSONObject bundle = ResourceBundleUtil.getBundle("hudson.logging.Messages", new Locale("kok")); // konkani
        Assert.assertEquals("Initialing log recorders", bundle.getString("LogRecorderManager.init"));
        
    }

    /**
     * Test unknown bundle.
     */
    @Test(expected = MissingResourceException.class)    
    public void test_unknown_bundle() {
        ResourceBundleUtil.getBundle("hudson.blah.Whatever");
    }
}
