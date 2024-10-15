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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.util.Locale;
import java.util.MissingResourceException;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class ResourceBundleUtilTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Test resource bundle loading for a defined locale.
     */
    @Test
    public void test_known_locale() {
        JSONObject bundle = ResourceBundleUtil.getBundle("hudson.logging.Messages", Locale.GERMAN);
        assertEquals("Initialisiere Log-Rekorder", bundle.getString("LogRecorderManager.init"));
        bundle = ResourceBundleUtil.getBundle("hudson.logging.Messages", new Locale("de"));
        assertEquals("Initialisiere Log-Rekorder", bundle.getString("LogRecorderManager.init"));

        // Test caching - should get the same bundle instance back...
        assertSame(ResourceBundleUtil.getBundle("hudson.logging.Messages", new Locale("de")), bundle);
    }

    @Test
    public void noFallbackLocale() {
        try (var ignored = new DefaultLocale(new Locale("fr"))) {
            var bundle = ResourceBundleUtil.getBundle("hudson.logging.Messages", new Locale("en"));
            assertEquals("System Log", bundle.getString("LogRecorderManager.DisplayName"));
        }
    }

    /**
     * Test that we get the "default" bundle for an unknown locale.
     */
    @Test
    public void test_unknown_locale() {
        JSONObject bundle = ResourceBundleUtil.getBundle("hudson.logging.Messages", new Locale("kok")); // konkani
        assertEquals("Initializing log recorders", bundle.getString("LogRecorderManager.init"));
    }

    /**
     * Test unknown bundle.
     */
    @Test
    public void test_unknown_bundle() {
        assertThrows(MissingResourceException.class, () -> ResourceBundleUtil.getBundle("hudson.blah.Whatever"));
    }

    private static class DefaultLocale implements AutoCloseable {
        private Locale previous;

        DefaultLocale(Locale locale) {
            previous = Locale.getDefault();
            Locale.setDefault(locale);
        }

        @Override
        public void close() {
            Locale.setDefault(previous);
        }
    }
}
