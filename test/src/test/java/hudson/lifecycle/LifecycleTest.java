/*
 * The MIT License
 *
 * Copyright 2023 CloudBees, Inc.
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

package hudson.lifecycle;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Field;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

class LifecycleTest {

    @RegisterExtension
    private final RealJenkinsExtension rr = new RealJenkinsExtension()
        .addPlugins("plugins/custom-lifecycle.hpi")
        .javaOptions("-Dhudson.lifecycle=test.custom_lifecycle.CustomLifecycle")
        .withLogger(Lifecycle.class, Level.FINE);

    @Test
    void definedInPlugin() throws Throwable {
        rr.then(LifecycleTest::_definedInPlugin);
    }

    private static void _definedInPlugin(JenkinsRule r) throws Throwable {
        Class<? extends Lifecycle> type = Jenkins.get().getPluginManager().uberClassLoader
            .loadClass("test.custom_lifecycle.CustomLifecycle").asSubclass(Lifecycle.class);
        Lifecycle l = Lifecycle.get();
        assertThat(l.getClass(), is(type));
        Field count = type.getField("count");
        assertThat(count.get(l), is(0));
        Lifecycle.get().restart();
        assertThat(count.get(l), is(1));
    }

}
