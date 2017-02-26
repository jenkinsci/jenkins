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

import jenkins.RestartRequiredException;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class PluginManagerUtil {

    public static JenkinsRule newJenkinsRule() {
        return new JenkinsRule() {
            @Override
            public void before() throws Throwable {
                setPluginManager(null);
                super.before();
            }
        };
    }

    public static void dynamicLoad(String plugin, Jenkins jenkins) throws IOException, InterruptedException, RestartRequiredException {
        dynamicLoad(plugin, jenkins, false);
    }

    public static void dynamicLoad(String plugin, Jenkins jenkins, boolean disable) throws IOException, InterruptedException, RestartRequiredException {
        URL src = PluginManagerTest.class.getClassLoader().getResource("plugins/" + plugin);
        File dest = new File(jenkins.getRootDir(), "plugins/" + plugin);
        FileUtils.copyURLToFile(src, dest);
        if (disable) {
            new File(dest.getPath() + ".disabled").createNewFile();
        }
        jenkins.pluginManager.dynamicLoad(dest);
    }
}
