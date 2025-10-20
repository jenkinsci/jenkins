/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

import hudson.remoting.ClassFilter;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import jenkins.util.BuildListenerAdapter;
import jenkins.util.TreeString;
import jenkins.util.TreeStringBuilder;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.WithPlugin;

@Tag("SmokeTest")
@WithJenkins
class CustomClassFilterTest {

    static {
        System.setProperty("hudson.remoting.ClassFilter", "javax.script.SimpleBindings,!jenkins.util.TreeString");
    }

    private final LogRecorder logging = new LogRecorder().record("jenkins.security", Level.FINER);

    @TempDir(cleanup = CleanupMode.NEVER)
    private File tmp;

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }


    @WithPlugin("custom-class-filter.jpi")
    @Test
    void smokes() {
        assertAll(
                () -> assertBlacklisted("enabled via system property", SimpleBindings.class, false),
                () -> assertBlacklisted("enabled via plugin", ScriptException.class, false),
                () -> assertBlacklisted("disabled by ClassFilter.STANDARD", ScriptEngineManager.class, true),
                () -> assertBlacklisted("part of Jenkins core, so why not?", BuildListenerAdapter.class, false),
                // As an aside, the following appear totally unused anyway!
                () -> assertBlacklisted("disabled via system property", TreeString.class, true),
                () -> assertBlacklisted("disabled via plugin", TreeStringBuilder.class, true)
        );
    }

    @Test
    void dynamicLoad() {
        assertAll(
                () -> assertBlacklisted("not yet enabled via plugin", ScriptException.class, true),
                () -> assertBlacklisted("not yet disabled via plugin", TreeStringBuilder.class, false),
                () -> {
                    File jpi = newFile(tmp, "custom-class-filter.jpi");
                    FileUtils.copyURLToFile(CustomClassFilterTest.class.getResource("/plugins/custom-class-filter.jpi"), jpi);
                    r.jenkins.pluginManager.dynamicLoad(jpi);
                },
                () -> assertBlacklisted("enabled via plugin", ScriptException.class, false),
                () -> assertBlacklisted("disabled via plugin", TreeStringBuilder.class, true)
        );
    }

    private void assertBlacklisted(String message, Class<?> c, boolean blacklisted) {
        String name = c.getName();
        assertThat(name + ": " + message, ClassFilter.DEFAULT.isBlacklisted(c) || ClassFilter.DEFAULT.isBlacklisted(name), is(blacklisted));
    }

    private static File newFile(File parent, String child) throws IOException {
        File result = new File(parent, child);
        result.createNewFile();
        return result;
    }

}
