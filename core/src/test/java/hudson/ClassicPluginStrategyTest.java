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

package hudson;

import static jenkins.plugins.DetachedPluginsUtil.DetachedPlugin;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.util.VersionNumber;
import java.util.List;
import jenkins.plugins.DetachedPluginsUtil;
import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
class ClassicPluginStrategyTest {

    @Test
    void test_getDetachedPlugins() {
        List<DetachedPlugin> list = DetachedPluginsUtil.getDetachedPlugins(new VersionNumber("1.296"));

        assertTrue(list.size() >= 14); // There were 14 at the time of writing this test
        assertNotNull(findPlugin("maven-plugin", list));
        assertNotNull(findPlugin("subversion", list));

        // Narrow the list to since "1.310" (the subversion detach version).
        list = DetachedPluginsUtil.getDetachedPlugins(new VersionNumber("1.310"));
        // Maven should no longer be in the list, but subversion should.
        assertNull(findPlugin("maven-plugin", list));
        assertNotNull(findPlugin("subversion", list));

        // Narrow the list to since "1.311" (after the subversion detach version).
        list = DetachedPluginsUtil.getDetachedPlugins(new VersionNumber("1.311"));
        // Neither Maven or subversion should be in the list.
        assertNull(findPlugin("maven-plugin", list));
        assertNull(findPlugin("subversion", list));
    }

    private DetachedPlugin findPlugin(String shortName, List<DetachedPlugin> list) {
        for (DetachedPlugin detachedPlugin : list) {
            if (detachedPlugin.getShortName().equals(shortName)) {
                return detachedPlugin;
            }
        }

        return null;
    }
}
