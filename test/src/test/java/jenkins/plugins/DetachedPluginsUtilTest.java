/*
 * The MIT License
 *
 * Copyright (c) 2025, the Jenkins project.
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

package jenkins.plugins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

/**
 * Tests for {@link DetachedPluginsUtil}, especially implied dependencies used when uploading plugins.
 */
class DetachedPluginsUtilTest {

    /**
     * JENKINS-33308: Plugins built for Jenkins &lt;= 1.535 get matrix-auth as an implied dependency
     * (it was detached from core at 1.535). Upload path must add these so manual uploads install correctly.
     */
    @Test
    @Issue("JENKINS-33308")
    void getImpliedDependenciesIncludesMatrixAuthForOldCore() {
        String pluginName = "ownership";
        String jenkinsVersion = "1.500"; // older than matrix-auth split (1.535)
        var deps = DetachedPluginsUtil.getImpliedDependencies(pluginName, jenkinsVersion);
        var shortNames = deps.stream().map(d -> d.shortName).toList();
        assertThat(
                "Plugin built for 1.500 should get matrix-auth as implied dependency",
                shortNames,
                hasItem("matrix-auth")
        );
    }

    /**
     * Same with null Jenkins version (old manifests): implied deps should still be returned.
     */
    @Test
    @Issue("JENKINS-33308")
    void getImpliedDependenciesWithNullJenkinsVersion() {
        var deps = DetachedPluginsUtil.getImpliedDependencies("some-plugin", null);
        assertTrue(
                deps.stream().anyMatch(d -> "matrix-auth".equals(d.shortName)),
                "Null Jenkins version should still imply matrix-auth for plugins needing it"
        );
    }
}
