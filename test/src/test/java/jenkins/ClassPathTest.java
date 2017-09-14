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

package jenkins;

import com.google.common.collect.Iterators;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.WarExploder;

public class ClassPathTest {

    @Rule
    public ErrorCollector errors = new ErrorCollector();

    @Ignore("TODO too many failures to solve them all now")
    @Issue("JENKINS-46754")
    @Test
    public void uniqueness() throws Exception {
        Map<String, List<String>> entries = new TreeMap<>();
        for (File jar : new File(WarExploder.getExplodedDir(), "WEB-INF/lib").listFiles((dir, name) -> name.endsWith(".jar"))) {
            String jarname = jar.getName();
            try (JarFile jf = new JarFile(jar)) {
                Iterators.forEnumeration(jf.entries()).forEachRemaining(e -> {
                    String name = e.getName();
                    if (name.startsWith("META-INF/") || name.endsWith("/") || !name.contains("/")) {
                        return;
                    }
                    entries.computeIfAbsent(name, k -> new ArrayList<>()).add(jarname);
                });
            }
        }
        entries.forEach((name, jarnames) -> {
            if (jarnames.size() > 1) { // Matchers.hasSize unfortunately does not display the collection
                errors.addError(new AssertionError(name + " duplicated in " + jarnames));
            }
        });
    }

}
