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

import hudson.remoting.ClassFilter;
import java.io.File;
import java.util.logging.Level;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import jenkins.util.BuildListenerAdapter;
import jenkins.util.TreeString;
import jenkins.util.TreeStringBuilder;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import static org.hamcrest.Matchers.*;
import org.junit.Rule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ErrorCollector;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.SmokeTest;
import org.jvnet.hudson.test.recipes.WithPlugin;

@Category(SmokeTest.class)
public class CustomClassFilterTest {

    static {
        System.setProperty("hudson.remoting.ClassFilter", "javax.script.SimpleBindings,!jenkins.util.TreeString");
    }

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public ErrorCollector errors = new ErrorCollector();

    @Rule
    public LoggerRule logging = new LoggerRule().record("jenkins.security", Level.FINER);

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @WithPlugin("custom-class-filter.jpi")
    @Test
    public void smokes() throws Exception {
        assertBlacklisted("enabled via system property", SimpleBindings.class, false);
        assertBlacklisted("enabled via plugin", ScriptException.class, false);
        assertBlacklisted("disabled by ClassFilter.STANDARD", ScriptEngineManager.class, true);
        assertBlacklisted("part of Jenkins core, so why not?", BuildListenerAdapter.class, false);
        // As an aside, the following appear totally unused anyway!
        assertBlacklisted("disabled via system property", TreeString.class, true);
        assertBlacklisted("disabled via plugin", TreeStringBuilder.class, true);
    }

    @Test
    public void dynamicLoad() throws Exception {
        assertBlacklisted("not yet enabled via plugin", ScriptException.class, true);
        assertBlacklisted("not yet disabled via plugin", TreeStringBuilder.class, false);
        File jpi = tmp.newFile("custom-class-filter.jpi");
        FileUtils.copyURLToFile(CustomClassFilterTest.class.getResource("/plugins/custom-class-filter.jpi"), jpi);
        r.jenkins.pluginManager.dynamicLoad(jpi);
        assertBlacklisted("enabled via plugin", ScriptException.class, false);
        assertBlacklisted("disabled via plugin", TreeStringBuilder.class, true);
    }

    private void assertBlacklisted(String message, Class<?> c, boolean blacklisted) {
        String name = c.getName();
        errors.checkThat(name + ": " + message, ClassFilter.DEFAULT.isBlacklisted(c) || ClassFilter.DEFAULT.isBlacklisted(name), is(blacklisted));
    }

}
